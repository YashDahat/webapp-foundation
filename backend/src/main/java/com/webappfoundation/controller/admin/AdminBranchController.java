package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.BranchDto;
import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.UpsertBranchRequest;
import com.webappfoundation.service.BranchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin branch management. ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**}). Writes are audited
 * inside {@link BranchService}.
 */
@RestController
@RequestMapping("/api/v1/admin/branches")
public class AdminBranchController {

    private final BranchService branchService;

    public AdminBranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public List<BranchDto> list() {
        return branchService.adminList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchDto create(@RequestBody UpsertBranchRequest request) {
        return branchService.create(request);
    }

    @PutMapping("/{id}")
    public BranchDto update(@PathVariable Long id, @RequestBody UpsertBranchRequest request) {
        return branchService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        branchService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/bulk-upload", consumes = "multipart/form-data")
    public BulkUploadResult bulkUpload(@RequestParam("file") MultipartFile file) {
        return branchService.bulkImport(file);
    }
}
