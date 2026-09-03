package com.jarvis.commerce.payment;

import com.jarvis.commerce.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/me/payments")
public class MePaymentController {

    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public MePaymentController(PaymentService paymentService, CurrentUser currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey) {
        return paymentService.createForUser(request, idempotencyKey.trim(), currentUser.id());
    }

    @GetMapping("/{paymentNo}")
    public PaymentResponse get(@PathVariable String paymentNo) {
        return paymentService.getForUser(paymentNo, currentUser.id());
    }

    @PostMapping("/{paymentNo}/close")
    public PaymentResponse close(@PathVariable String paymentNo) {
        return paymentService.closeForUser(paymentNo, currentUser.id());
    }

    @PostMapping("/{paymentNo}/retry")
    public PaymentResponse retry(@PathVariable String paymentNo) {
        return paymentService.retryForUser(paymentNo, currentUser.id());
    }
}
