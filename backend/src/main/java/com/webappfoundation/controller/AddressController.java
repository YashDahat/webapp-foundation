package com.webappfoundation.controller;

import com.webappfoundation.dto.UpsertAddressRequest;
import com.webappfoundation.dto.UserAddressDto;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.service.AddressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The user's address book. Identity is server-derived via {@code @CurrentUser}. User-owned (not an admin
 * entity) → no bulk-upload.
 */
@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<UserAddressDto> list(@CurrentUser Integer userId) {
        return addressService.list(requireUser(userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAddressDto add(@CurrentUser Integer userId, @RequestBody UpsertAddressRequest request) {
        return addressService.add(requireUser(userId), request);
    }

    @PutMapping("/{id}")
    public UserAddressDto update(@CurrentUser Integer userId, @PathVariable Long id, @RequestBody UpsertAddressRequest request) {
        return addressService.update(requireUser(userId), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@CurrentUser Integer userId, @PathVariable Long id) {
        addressService.remove(requireUser(userId), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/default")
    public UserAddressDto setDefault(@CurrentUser Integer userId, @PathVariable Long id) {
        return addressService.setDefault(requireUser(userId), id);
    }

    private Integer requireUser(Integer userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        }
        return userId;
    }
}
