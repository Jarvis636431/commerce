package com.jarvis.commerce.order;

import com.jarvis.commerce.auth.CurrentUser;
import com.jarvis.commerce.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/me/orders")
public class MeOrderController {

    private final OrderService orderService;
    private final CurrentUser currentUser;

    public MeOrderController(OrderService orderService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateMyOrderRequest request) {
        return orderService.create(new CreateOrderRequest(currentUser.id(), request.addressId(), request.items()));
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @PageableDefault(size = 20, sort = "id", direction = DESC) Pageable pageable) {
        return orderService.listByUser(currentUser.id(), pageable);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable long orderId) {
        return orderService.getForUser(orderId, currentUser.id());
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable long orderId) {
        return orderService.cancelForUser(orderId, currentUser.id());
    }
}
