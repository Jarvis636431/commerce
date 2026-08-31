package com.jarvis.commerce.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/addresses")
public class UserAddressController {

    private final UserAddressService addressService;

    public UserAddressController(UserAddressService addressService) { this.addressService = addressService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@PathVariable long userId, @Valid @RequestBody AddressRequest request) {
        return addressService.create(userId, request);
    }

    @GetMapping
    public List<AddressResponse> list(@PathVariable long userId) { return addressService.list(userId); }

    @GetMapping("/{addressId}")
    public AddressResponse get(@PathVariable long userId, @PathVariable long addressId) {
        return addressService.get(userId, addressId);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(@PathVariable long userId, @PathVariable long addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(userId, addressId, request);
    }

    @PostMapping("/{addressId}/default")
    public AddressResponse makeDefault(@PathVariable long userId, @PathVariable long addressId) {
        return addressService.makeDefault(userId, addressId);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long userId, @PathVariable long addressId) {
        addressService.delete(userId, addressId);
    }
}
