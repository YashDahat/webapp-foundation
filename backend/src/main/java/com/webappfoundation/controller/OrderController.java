package com.webappfoundation.controller;

import com.webappfoundation.dto.CancelOrderRequest;
import com.webappfoundation.dto.CreateFeedbackRequest;
import com.webappfoundation.dto.OrderDto;
import com.webappfoundation.dto.OrderFeedbackDto;
import com.webappfoundation.dto.PlaceOrderRequest;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.service.FeedbackService;
import com.webappfoundation.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Customer order endpoints. Identity is server-derived via {@code @CurrentUser} — never sent by the client.
 * Reachable paths are permitted by SecurityConfig, so each method rejects an unauthenticated caller (401).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final FeedbackService feedbackService;

    public OrderController(OrderService orderService, FeedbackService feedbackService) {
        this.orderService = orderService;
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto place(@CurrentUser Integer userId, @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(requireUser(userId), request);
    }

    @GetMapping
    public List<OrderDto> myOrders(@CurrentUser Integer userId) {
        return orderService.myOrders(requireUser(userId));
    }

    @GetMapping("/{id}")
    public OrderDto get(@CurrentUser Integer userId, @PathVariable Long id) {
        return orderService.get(requireUser(userId), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderDto cancel(@CurrentUser Integer userId, @PathVariable Long id,
                           @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request == null ? null : request.getReason();
        return orderService.cancel(id, requireUser(userId), reason, false);
    }

    @PostMapping("/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderFeedbackDto feedback(@CurrentUser Integer userId, @PathVariable Long id,
                                     @RequestBody CreateFeedbackRequest request) {
        return feedbackService.submit(requireUser(userId), id, request);
    }

    private Integer requireUser(Integer userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        }
        return userId;
    }
}
