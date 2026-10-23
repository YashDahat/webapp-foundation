package com.webappfoundation.service;

import com.webappfoundation.dto.BranchDto;
import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.UpsertBranchRequest;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.Branch;
import com.webappfoundation.repository.BranchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Outlet management + the public "nearest branch" lookup. Branch counts are small, so {@code nearest}
 * sorts active branches by haversine distance in memory. Admin writes are audited.
 */
@Service
public class BranchService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final BranchRepository branchRepository;
    private final SpreadsheetParser spreadsheetParser;
    private final AuditService auditService;

    public BranchService(BranchRepository branchRepository, SpreadsheetParser spreadsheetParser,
                         AuditService auditService) {
        this.branchRepository = branchRepository;
        this.spreadsheetParser = spreadsheetParser;
        this.auditService = auditService;
    }

    // ── Public ──

    public List<BranchDto> listActive() {
        return branchRepository.findByActiveTrue().stream().map(b -> toDto(b, null)).toList();
    }

    public List<BranchDto> nearest(double lat, double lng) {
        return branchRepository.findByActiveTrue().stream()
                .map(b -> toDto(b, distanceKm(lat, lng, b)))
                .sorted(Comparator.comparing(d -> d.getDistanceKm() == null ? Double.MAX_VALUE : d.getDistanceKm()))
                .toList();
    }

    public BranchDto getBySlug(String slug) {
        return toDto(branchRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + slug)), null);
    }

    // ── Admin ──

    public List<BranchDto> adminList() {
        return branchRepository.findAll().stream().map(b -> toDto(b, null)).toList();
    }

    @Transactional
    public BranchDto create(UpsertBranchRequest req) {
        validate(req);
        String slug = resolveSlug(req, null);
        if (branchRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A branch with slug '" + slug + "' already exists.");
        }
        Instant now = Instant.now();
        Branch branch = Branch.builder().slug(slug).createdAt(now).updatedAt(now).build();
        apply(branch, req);
        Branch saved = branchRepository.save(branch);
        auditService.record(AuditActions.BRANCH_CREATED, AuditActions.SUCCESS, "BRANCH",
                String.valueOf(saved.getId()), "name=" + saved.getName());
        return toDto(saved, null);
    }

    @Transactional
    public BranchDto update(Long id, UpsertBranchRequest req) {
        validate(req);
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + id));
        String slug = resolveSlug(req, branch);
        if (!slug.equals(branch.getSlug()) && branchRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A branch with slug '" + slug + "' already exists.");
        }
        branch.setSlug(slug);
        apply(branch, req);
        branch.setUpdatedAt(Instant.now());
        Branch saved = branchRepository.save(branch);
        auditService.record(AuditActions.BRANCH_UPDATED, AuditActions.SUCCESS, "BRANCH",
                String.valueOf(saved.getId()), "name=" + saved.getName());
        return toDto(saved, null);
    }

    @Transactional
    public void delete(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + id));
        branchRepository.delete(branch);
        auditService.record(AuditActions.BRANCH_DELETED, AuditActions.SUCCESS, "BRANCH",
                String.valueOf(id), "name=" + branch.getName());
    }

    /**
     * Bulk-import branches. Columns: {@code name, phone, addressLine, city, state, pincode, latitude,
     * longitude, googleMapsUrl, openingHours} (name required). Duplicate slug/name rows are skipped.
     */
    @Transactional
    public BulkUploadResult bulkImport(MultipartFile file) {
        BulkUploadResult result = BulkUploadResult.builder().build();
        List<Map<String, String>> rows = spreadsheetParser.parse(file);
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String name = row.getOrDefault("name", "").trim();
            if (name.isEmpty()) {
                result.addError(rowNum, "name is required");
                continue;
            }
            String slug = slugify(name);
            if (branchRepository.existsBySlug(slug) || branchRepository.existsByNameIgnoreCase(name)) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            Instant now = Instant.now();
            branchRepository.save(Branch.builder()
                    .name(name)
                    .slug(slug)
                    .phone(emptyToNull(row.getOrDefault("phone", "")))
                    .addressLine(emptyToNull(row.getOrDefault("addressline", "")))
                    .city(emptyToNull(row.getOrDefault("city", "")))
                    .state(emptyToNull(row.getOrDefault("state", "")))
                    .pincode(emptyToNull(row.getOrDefault("pincode", "")))
                    .latitude(parseDouble(row.get("latitude")))
                    .longitude(parseDouble(row.get("longitude")))
                    .googleMapsUrl(emptyToNull(row.getOrDefault("googlemapsurl", "")))
                    .openingHours(emptyToNull(row.getOrDefault("openinghours", "")))
                    .active(true)
                    .createdAt(now).updatedAt(now)
                    .build());
            result.setCreated(result.getCreated() + 1);
        }
        auditService.record(AuditActions.BRANCH_BULK_IMPORT, AuditActions.SUCCESS, "BRANCH", null,
                "created=" + result.getCreated() + " skipped=" + result.getSkipped() + " failed=" + result.getFailed());
        return result;
    }

    // ── Helpers ──

    private void validate(UpsertBranchRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
    }

    private void apply(Branch b, UpsertBranchRequest req) {
        b.setName(req.getName().trim());
        b.setPhone(emptyToNull(req.getPhone()));
        b.setAddressLine(emptyToNull(req.getAddressLine()));
        b.setCity(emptyToNull(req.getCity()));
        b.setState(emptyToNull(req.getState()));
        b.setPincode(emptyToNull(req.getPincode()));
        b.setLatitude(req.getLatitude());
        b.setLongitude(req.getLongitude());
        b.setGoogleMapsUrl(emptyToNull(req.getGoogleMapsUrl()));
        b.setOpeningHours(emptyToNull(req.getOpeningHours()));
        b.setActive(req.getActive() == null || req.getActive());
    }

    private String resolveSlug(UpsertBranchRequest req, Branch existing) {
        if (req.getSlug() != null && !req.getSlug().isBlank()) {
            return slugify(req.getSlug());
        }
        if (existing != null && existing.getSlug() != null) {
            return existing.getSlug();
        }
        return slugify(req.getName());
    }

    private Double distanceKm(double lat, double lng, Branch b) {
        if (b.getLatitude() == null || b.getLongitude() == null) {
            return null;
        }
        double dLat = Math.toRadians(b.getLatitude() - lat);
        double dLng = Math.toRadians(b.getLongitude() - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private BranchDto toDto(Branch b, Double distanceKm) {
        return BranchDto.builder()
                .id(b.getId()).name(b.getName()).slug(b.getSlug()).phone(b.getPhone())
                .addressLine(b.getAddressLine()).city(b.getCity()).state(b.getState()).pincode(b.getPincode())
                .latitude(b.getLatitude()).longitude(b.getLongitude())
                .googleMapsUrl(b.getGoogleMapsUrl()).openingHours(b.getOpeningHours())
                .active(b.isActive())
                .distanceKm(distanceKm == null ? null : Math.round(distanceKm * 10) / 10.0)
                .createdAt(b.getCreatedAt()).updatedAt(b.getUpdatedAt())
                .build();
    }

    private static String slugify(String raw) {
        String slug = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "branch" : slug;
    }

    private static String emptyToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static Double parseDouble(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return null; }
    }
}
