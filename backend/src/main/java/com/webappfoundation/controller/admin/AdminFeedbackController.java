package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.OrderFeedbackDto;
import com.webappfoundation.service.FeedbackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin read-only feedback view (like {@code /admin/logs}). ADMIN-gated by SecurityConfig. User-generated
 * + read-only → no bulk-upload.
 */
@RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public List<OrderFeedbackDto> all() {
        return feedbackService.all();
    }
}
