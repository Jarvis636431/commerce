package com.jarvis.commerce.cart;

import com.jarvis.commerce.auth.CurrentUser;
import com.jarvis.commerce.order.OrderResponse;
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
@RequestMapping("/api/me/cart")
public class MeCartController {

    private final CartService cartService;
    private final CurrentUser currentUser;

    public MeCartController(CartService cartService, CurrentUser currentUser) {
        this.cartService = cartService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public CartResponse get() { return cartService.get(currentUser.id()); }

    @PostMapping("/items")
    public CartResponse add(@Valid @RequestBody AddCartItemRequest request) {
        return cartService.add(currentUser.id(), request);
    }

    @PutMapping("/items/{skuId}")
    public CartResponse update(@PathVariable long skuId, @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.update(currentUser.id(), skuId, request);
    }

    @DeleteMapping("/items/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long skuId) { cartService.remove(currentUser.id(), skuId); }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() { cartService.clear(currentUser.id()); }

    @PostMapping("/checkout")
    public OrderResponse checkout(@Valid @RequestBody CheckoutCartRequest request) {
        return cartService.checkout(currentUser.id(), request);
    }
}
