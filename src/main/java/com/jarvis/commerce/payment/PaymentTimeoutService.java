package com.jarvis.commerce.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class PaymentTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutService.class);

    private final PaymentOrderRepository paymentRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    public PaymentTimeoutService(PaymentOrderRepository paymentRepository,
                                 PaymentService paymentService,
                                 Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${commerce.payment.timeout-scan-interval:60000}")
    public void expireDuePayments() {
        expireDuePayments(OffsetDateTime.now(clock));
    }

    public int expireDuePayments(OffsetDateTime now) {
        int expiredCount = 0;
        for (PaymentOrder payment : paymentRepository
                .findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(PaymentStatus.PENDING, now)) {
            try {
                if (paymentService.expireIfDue(payment.getPaymentNo(), now)) {
                    expiredCount++;
                }
            } catch (RuntimeException exception) {
                log.warn("Failed to expire payment {}", payment.getPaymentNo(), exception);
            }
        }
        return expiredCount;
    }
}
