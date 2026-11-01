package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.CreateOfferRequest;
import com.webappfoundation.dto.OfferDto;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.service.OfferAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin management of discount codes ({@code Offer}s). ADMIN-gated by SecurityConfig. Manual codes are
 * recorded with the approving staff id ({@code @CurrentUser}) and audited. Bulk-upload is compulsory for
 * admin entity tables — here it imports public campaign codes.
 */
@RestController
@RequestMapping("/api/v1/admin/loyalty/offers")
public class AdminOfferController {

    private final OfferAdminService offerAdminService;

    public AdminOfferController(OfferAdminService offerAdminService) {
        this.offerAdminService = offerAdminService;
    }

    @GetMapping
    public List<OfferDto> list() {
        return offerAdminService.adminList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDto create(@RequestBody CreateOfferRequest request, @CurrentUser Integer staffId) {
        return offerAdminService.create(request, staffId);
    }

    @PostMapping("/{id}/revoke")
    public OfferDto revoke(@PathVariable Long id) {
        return offerAdminService.revoke(id);
    }

    @PostMapping(value = "/bulk-upload", consumes = "multipart/form-data")
    public BulkUploadResult bulkUpload(@RequestParam("file") MultipartFile file) {
        return offerAdminService.bulkImport(file);
    }
}
