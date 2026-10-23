package com.webappfoundation.service;

import com.webappfoundation.dto.UpsertAddressRequest;
import com.webappfoundation.dto.UserAddressDto;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.UserAddress;
import com.webappfoundation.repository.UserAddressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * The user address book: list / add / update / remove / set-default, all scoped to the current user
 * ({@code Integer userId}, never from the client). Setting a default clears the previous one. Audited.
 */
@Service
public class AddressService {

    private final UserAddressRepository repository;
    private final AuditService auditService;

    public AddressService(UserAddressRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<UserAddressDto> list(Integer userId) {
        return repository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public UserAddressDto add(Integer userId, UpsertAddressRequest req) {
        boolean makeDefault = Boolean.TRUE.equals(req.getIsDefault())
                || repository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).isEmpty(); // first address = default
        if (makeDefault) {
            clearDefault(userId);
        }
        UserAddress address = UserAddress.builder()
                .userId(userId)
                .isDefault(makeDefault)
                .createdAt(Instant.now())
                .build();
        apply(address, req);
        UserAddress saved = repository.save(address);
        auditService.record(AuditActions.ADDRESS_ADDED, AuditActions.SUCCESS, "ADDRESS",
                String.valueOf(saved.getId()), "label=" + safe(saved.getLabel()));
        return toDto(saved);
    }

    @Transactional
    public UserAddressDto update(Integer userId, Long id, UpsertAddressRequest req) {
        UserAddress address = owned(userId, id);
        if (Boolean.TRUE.equals(req.getIsDefault()) && !address.isDefault()) {
            clearDefault(userId);
            address.setDefault(true);
        }
        apply(address, req);
        UserAddress saved = repository.save(address);
        auditService.record(AuditActions.ADDRESS_UPDATED, AuditActions.SUCCESS, "ADDRESS",
                String.valueOf(saved.getId()), "label=" + safe(saved.getLabel()));
        return toDto(saved);
    }

    @Transactional
    public void remove(Integer userId, Long id) {
        UserAddress address = owned(userId, id);
        repository.delete(address);
        auditService.record(AuditActions.ADDRESS_DELETED, AuditActions.SUCCESS, "ADDRESS", String.valueOf(id), null);
    }

    @Transactional
    public UserAddressDto setDefault(Integer userId, Long id) {
        UserAddress address = owned(userId, id);
        clearDefault(userId);
        address.setDefault(true);
        UserAddress saved = repository.save(address);
        auditService.record(AuditActions.ADDRESS_DEFAULT_SET, AuditActions.SUCCESS, "ADDRESS", String.valueOf(id), null);
        return toDto(saved);
    }

    /** Resolve + ownership-check an address the current user owns (used by the order spine too). */
    public UserAddress requireOwned(Integer userId, Long id) {
        return owned(userId, id);
    }

    private UserAddress owned(Integer userId, Long id) {
        UserAddress address = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + id));
        if (!address.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your address");
        }
        return address;
    }

    private void clearDefault(Integer userId) {
        repository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
                .filter(UserAddress::isDefault)
                .forEach(a -> { a.setDefault(false); repository.save(a); });
    }

    private void apply(UserAddress a, UpsertAddressRequest req) {
        a.setLabel(emptyToNull(req.getLabel()));
        a.setContactName(emptyToNull(req.getContactName()));
        a.setPhone(emptyToNull(req.getPhone()));
        a.setLine1(emptyToNull(req.getLine1()));
        a.setLine2(emptyToNull(req.getLine2()));
        a.setCity(emptyToNull(req.getCity()));
        a.setState(emptyToNull(req.getState()));
        a.setPincode(emptyToNull(req.getPincode()));
        a.setLatitude(req.getLatitude());
        a.setLongitude(req.getLongitude());
    }

    private UserAddressDto toDto(UserAddress a) {
        return UserAddressDto.builder()
                .id(a.getId()).label(a.getLabel()).contactName(a.getContactName()).phone(a.getPhone())
                .line1(a.getLine1()).line2(a.getLine2()).city(a.getCity()).state(a.getState()).pincode(a.getPincode())
                .latitude(a.getLatitude()).longitude(a.getLongitude()).isDefault(a.isDefault()).createdAt(a.getCreatedAt())
                .build();
    }

    private static String emptyToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
