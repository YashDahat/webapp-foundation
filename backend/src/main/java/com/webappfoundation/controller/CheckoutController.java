package com.webappfoundation.controller;

import com.webappfoundation.dto.QuoteRequest;
import com.webappfoundation.dto.QuoteResponse;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout-time quote endpoint: runs the quote connectors for the current cart + fulfilment + address and
 * returns the priced options the client picks from (re-validated server-side at placeOrder).
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final OrderService orderService;

    public CheckoutController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@CurrentUser Integer userId, @RequestBody QuoteRequest request) {
        return orderService.quote(userId, request);
    }
}
