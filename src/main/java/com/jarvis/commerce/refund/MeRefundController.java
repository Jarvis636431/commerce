package com.jarvis.commerce.refund;

import com.jarvis.commerce.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/me/refunds")
public class MeRefundController {
    private final RefundService refundService;
    private final CurrentUser currentUser;

    public MeRefundController(RefundService refundService, CurrentUser currentUser) {
        this.refundService = refundService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse create(@Valid @RequestBody CreateRefundRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey) {
        return refundService.createForUser(request, idempotencyKey.trim(), currentUser.id());
    }

    @GetMapping("/{refundNo}")
    public RefundResponse get(@PathVariable String refundNo) {
        return refundService.getForUser(refundNo, currentUser.id());
    }
}
