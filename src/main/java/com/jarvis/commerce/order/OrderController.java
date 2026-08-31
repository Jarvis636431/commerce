package com.jarvis.commerce.order;

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
import org.springframework.web.bind.annotation.RequestParam;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable long id) {
        return orderService.get(id);
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "id", direction = DESC) Pageable pageable) {
        return userId == null ? orderService.list(pageable) : orderService.listByUser(userId, pageable);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable long id) {
        return orderService.cancel(id);
    }

    @PostMapping("/{id}/complete")
    public OrderResponse complete(@PathVariable long id) {
        return orderService.complete(id);
    }
}
