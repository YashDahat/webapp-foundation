package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.AdvanceStageRequest;
import com.webappfoundation.dto.AssignOrderRequest;
import com.webappfoundation.dto.BlockOrderRequest;
import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.CancelOrderRequest;
import com.webappfoundation.dto.OrderDto;
import com.webappfoundation.dto.RefundOrderRequest;
import com.webappfoundation.service.OrderService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin order management. ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**}). Advance re-fires the
 * target stage's actions; cancel here overrides the stage cancellation policy; mark-paid settles a
 * pay-at-counter pickup. Writes are audited inside {@link OrderService}.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderDto> all() {
        return orderService.all();
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id) {
        return orderService.getAdmin(id);
    }

    @PutMapping("/{id}/stage")
    public OrderDto advance(@PathVariable Long id, @RequestBody AdvanceStageRequest request) {
        return orderService.advanceStage(id, request.getStageCode());
    }

    @PostMapping("/{id}/cancel")
    public OrderDto cancel(@PathVariable Long id, @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request == null ? null : request.getReason();
        return orderService.cancel(id, null, reason, true); // admin override
    }

    @PostMapping("/{id}/mark-paid")
    public OrderDto markPaid(@PathVariable Long id) {
        return orderService.markPaid(id);
    }

    // ── Order board (block/resume are STAFF-permitted; assignee/payment-link/refund are ADMIN-only) ──

    @PostMapping("/{id}/block")
    public OrderDto block(@PathVariable Long id, @RequestBody BlockOrderRequest request) {
        return orderService.block(id, request.getReason(), request.getNote());
    }

    @PostMapping("/{id}/resume")
    public OrderDto resume(@PathVariable Long id) {
        return orderService.resume(id);
    }

    @PutMapping("/{id}/assignee")
    public OrderDto assign(@PathVariable Long id, @RequestBody AssignOrderRequest request) {
        return orderService.assign(id, request.getUserId());
    }

    @PostMapping("/{id}/send-payment-link")
    public OrderDto sendPaymentLink(@PathVariable Long id) {
        return orderService.sendPaymentLink(id);
    }

    @PostMapping("/{id}/refund")
    public OrderDto refund(@PathVariable Long id, @RequestBody(required = false) RefundOrderRequest request) {
        BigDecimal amount = request == null ? null : request.getAmount();
        String reason = request == null ? null : request.getReason();
        return orderService.refund(id, amount, reason);
    }

    @PostMapping(value = "/bulk-upload", consumes = "multipart/form-data")
    public BulkUploadResult bulkUpload(@RequestParam("file") MultipartFile file) {
        return orderService.bulkImport(file);
    }
}
