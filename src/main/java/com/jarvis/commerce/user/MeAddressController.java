package com.jarvis.commerce.user;

import com.jarvis.commerce.auth.CurrentUser;
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
@RequestMapping("/api/me/addresses")
public class MeAddressController {

    private final UserAddressService addressService;
    private final CurrentUser currentUser;

    public MeAddressController(UserAddressService addressService, CurrentUser currentUser) {
        this.addressService = addressService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@Valid @RequestBody AddressRequest request) {
        return addressService.create(currentUser.id(), request);
    }

    @GetMapping
    public List<AddressResponse> list() { return addressService.list(currentUser.id()); }

    @GetMapping("/{addressId}")
    public AddressResponse get(@PathVariable long addressId) {
        return addressService.get(currentUser.id(), addressId);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(@PathVariable long addressId, @Valid @RequestBody AddressRequest request) {
        return addressService.update(currentUser.id(), addressId, request);
    }

    @PostMapping("/{addressId}/default")
    public AddressResponse makeDefault(@PathVariable long addressId) {
        return addressService.makeDefault(currentUser.id(), addressId);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long addressId) { addressService.delete(currentUser.id(), addressId); }
}
