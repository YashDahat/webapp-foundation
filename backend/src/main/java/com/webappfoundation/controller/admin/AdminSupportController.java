package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.AdminReplyRequest;
import com.webappfoundation.dto.SupportCaseDto;
import com.webappfoundation.dto.SupportThreadDto;
import com.webappfoundation.service.SupportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin helpdesk: list all support cases, read a thread, and reply (adds a STAFF message, may change
 * status, and emails the customer). ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/support")
public class AdminSupportController {

    private final SupportService supportService;

    public AdminSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    public List<SupportCaseDto> all() {
        return supportService.all();
    }

    @GetMapping("/{id}")
    public SupportThreadDto thread(@PathVariable Long id) {
        return supportService.adminThread(id);
    }

    @PostMapping("/{id}/reply")
    public SupportThreadDto reply(@PathVariable Long id, @RequestBody AdminReplyRequest request) {
        return supportService.adminReply(id, request);
    }
}
