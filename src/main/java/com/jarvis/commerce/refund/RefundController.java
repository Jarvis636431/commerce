package com.jarvis.commerce.refund;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/refunds")
public class RefundController {
    private final RefundService refundService;

    public RefundController(RefundService refundService) { this.refundService = refundService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse create(@Valid @RequestBody CreateRefundRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey) {
        return refundService.create(request, idempotencyKey.trim());
    }

    @GetMapping("/{refundNo}")
    public RefundResponse get(@PathVariable String refundNo) { return refundService.get(refundNo); }

    @PostMapping("/{refundNo}/mock-success")
    public RefundResponse mockSuccess(@PathVariable String refundNo,
            @Valid @RequestBody RefundSuccessNotification notification) {
        return refundService.handleSuccess(refundNo, notification);
    }

    @PostMapping("/{refundNo}/mock-failure")
    public RefundResponse mockFailure(@PathVariable String refundNo,
            @Valid @RequestBody RefundFailureNotification notification) {
        return refundService.handleFailure(refundNo, notification);
    }
}
