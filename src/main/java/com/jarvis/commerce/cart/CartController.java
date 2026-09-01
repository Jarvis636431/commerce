package com.jarvis.commerce.cart;

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

@RestController
@RequestMapping("/api/users/{userId}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) { this.cartService = cartService; }

    @GetMapping
    public CartResponse get(@PathVariable long userId) { return cartService.get(userId); }

    @PostMapping("/items")
    public CartResponse add(@PathVariable long userId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.add(userId, request);
    }

    @PutMapping("/items/{skuId}")
    public CartResponse update(@PathVariable long userId, @PathVariable long skuId,
                               @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.update(userId, skuId, request);
    }

    @DeleteMapping("/items/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long userId, @PathVariable long skuId) {
        cartService.remove(userId, skuId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable long userId) { cartService.clear(userId); }
}
