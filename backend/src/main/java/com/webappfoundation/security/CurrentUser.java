package com.webappfoundation.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated user's id ({@code User.id}, an {@code Integer}) into a controller
 * parameter, or {@code null} for a guest / unauthenticated request. This is the ONE way a domain
 * controller learns "who is the current user" — never parse the principal by hand, never invent an
 * id, never a UUID. Every user-owned record links by {@code Integer userId}.
 *
 * <pre>{@code
 * @PostMapping("/orders")
 * public OrderDto create(@CurrentUser Integer userId, @RequestBody CreateOrderRequest req) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
