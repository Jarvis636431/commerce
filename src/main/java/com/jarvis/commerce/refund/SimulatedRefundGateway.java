package com.jarvis.commerce.refund;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatedRefundGateway implements RefundGateway {
    private static final Logger log = LoggerFactory.getLogger(SimulatedRefundGateway.class);

    @Override
    public void submit(RefundSubmission submission) {
        log.info("Simulated refund channel accepted request: refundNo={}, paymentNo={}, amount={}",
                submission.refundNo(), submission.paymentNo(), submission.amount());
    }
}
