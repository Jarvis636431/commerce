package com.jarvis.commerce.payment;

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
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey) {
        return paymentService.create(request, idempotencyKey.trim());
    }

    @GetMapping("/{paymentNo}")
    public PaymentResponse get(@PathVariable String paymentNo) {
        return paymentService.get(paymentNo);
    }

    @PostMapping("/{paymentNo}/mock-success")
    public PaymentResponse mockSuccess(@PathVariable String paymentNo,
                                       @Valid @RequestBody PaymentSuccessNotification notification) {
        return paymentService.handleSuccess(paymentNo, notification);
    }

    @PostMapping("/{paymentNo}/mock-failure")
    public PaymentResponse mockFailure(@PathVariable String paymentNo,
                                       @Valid @RequestBody PaymentFailureNotification notification) {
        return paymentService.handleFailure(paymentNo, notification);
    }

    @PostMapping("/{paymentNo}/close")
    public PaymentResponse close(@PathVariable String paymentNo) {
        return paymentService.close(paymentNo);
    }

    @PostMapping("/{paymentNo}/retry")
    public PaymentResponse retry(@PathVariable String paymentNo) {
        return paymentService.retry(paymentNo);
    }
}
