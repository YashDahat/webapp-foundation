package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A customer review of a {@link Product}: 1–5 stars + optional comment, owned by {@code Integer userId}
 * (@CurrentUser). One review per (product, user) — the unique constraint makes submit an upsert (a user
 * edits their own review). {@code userName} is a display-name snapshot taken at write time. Admins read and
 * moderate (delete) reviews at {@code /admin/reviews}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_review", uniqueConstraints = @UniqueConstraint(columnNames = {"productId", "userId"}))
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer userId;

    /** Display-name snapshot of the reviewer at write time (not a live link to the user). */
    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
