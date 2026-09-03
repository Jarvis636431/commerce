package com.jarvis.commerce.refund;

import java.math.BigDecimal;

public record RefundSubmission(String refundNo, String paymentNo, BigDecimal amount, String reason) {}
