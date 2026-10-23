package com.webappfoundation.controller;

import com.webappfoundation.dto.BranchDto;
import com.webappfoundation.service.BranchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public branch reads. Unauthenticated ({@code anyRequest().permitAll()} in SecurityConfig).
 * {@code /nearest} sorts active branches by distance from the given coordinates.
 */
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public List<BranchDto> list() {
        return branchService.listActive();
    }

    @GetMapping("/nearest")
    public List<BranchDto> nearest(@RequestParam double lat, @RequestParam double lng) {
        return branchService.nearest(lat, lng);
    }

    @GetMapping("/slug/{slug}")
    public BranchDto getBySlug(@PathVariable String slug) {
        return branchService.getBySlug(slug);
    }
}
