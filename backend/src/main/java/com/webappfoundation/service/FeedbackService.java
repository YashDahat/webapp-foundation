package com.webappfoundation.service;

import com.webappfoundation.dto.CreateFeedbackRequest;
import com.webappfoundation.dto.OrderFeedbackDto;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.OrderFeedback;
import com.webappfoundation.repository.OrderFeedbackRepository;
import com.webappfoundation.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Captures a tiny post-checkout rating (1–5 stars + optional comment), one per order. Submit is
 * ownership-checked and submit-once; admin reads are read-only. The comment is user-generated and not
 * treated as sensitive, but like all writes it's audited.
 */
@Service
public class FeedbackService {

    private final OrderFeedbackRepository feedbackRepository;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    public FeedbackService(OrderFeedbackRepository feedbackRepository, OrderRepository orderRepository,
                           AuditService auditService) {
        this.feedbackRepository = feedbackRepository;
        this.orderRepository = orderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public OrderFeedbackDto submit(Integer userId, Long orderId, CreateFeedbackRequest req) {
        if (req == null || req.getRating() < 1 || req.getRating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
        }
        if (feedbackRepository.existsByOrderId(orderId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Feedback was already submitted for this order");
        }
        OrderFeedback saved = feedbackRepository.save(OrderFeedback.builder()
                .orderId(orderId)
                .userId(userId)
                .rating(req.getRating())
                .comment(trimToNull(req.getComment()))
                .createdAt(Instant.now())
                .build());
        auditService.record(AuditActions.FEEDBACK_SUBMITTED, AuditActions.SUCCESS, "ORDER", String.valueOf(orderId),
                "rating=" + saved.getRating());
        return toDto(saved, order.getOrderNumber());
    }

    public List<OrderFeedbackDto> all() {
        List<OrderFeedback> rows = feedbackRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> orderNumbers = orderRepository.findAllById(
                        rows.stream().map(OrderFeedback::getOrderId).toList()).stream()
                .collect(Collectors.toMap(Order::getId, Order::getOrderNumber, (a, b) -> a));
        return rows.stream().map(f -> toDto(f, orderNumbers.get(f.getOrderId()))).toList();
    }

    private OrderFeedbackDto toDto(OrderFeedback f, String orderNumber) {
        return OrderFeedbackDto.builder()
                .id(f.getId()).orderId(f.getOrderId()).orderNumber(orderNumber)
                .rating(f.getRating()).comment(f.getComment()).createdAt(f.getCreatedAt())
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
