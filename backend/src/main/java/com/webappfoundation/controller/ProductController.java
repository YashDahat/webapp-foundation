package com.webappfoundation.controller;

import com.webappfoundation.dto.ProductDto;
import com.webappfoundation.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public catalog reads for the default {@link com.webappfoundation.model.Product}. Unauthenticated
 * ({@code anyRequest().permitAll()} in SecurityConfig). Pass {@code branchId} to return only items offered
 * at that branch; omit it for the global catalog.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductDto> list(@RequestParam(required = false) String category,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(required = false) Long branchId) {
        return productService.listActive(category, q, branchId);
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping("/slug/{slug}")
    public ProductDto getBySlug(@PathVariable String slug) {
        return productService.getBySlug(slug);
    }
}
