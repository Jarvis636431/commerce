package com.jarvis.commerce.refund;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import com.jarvis.commerce.order.CustomerOrder;
import com.jarvis.commerce.payment.PaymentOrder;
import com.jarvis.commerce.payment.PaymentOrderRepository;
import com.jarvis.commerce.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RefundService {
    private final RefundOrderRepository refundRepository;
    private final RefundNotificationRepository notificationRepository;
    private final PaymentOrderRepository paymentRepository;
    private final RefundMetrics metrics;

    public RefundService(RefundOrderRepository refundRepository,
                         RefundNotificationRepository notificationRepository,
                         PaymentOrderRepository paymentRepository, RefundMetrics metrics) {
        this.refundRepository = refundRepository;
        this.notificationRepository = notificationRepository;
        this.paymentRepository = paymentRepository;
        this.metrics = metrics;
    }

    @Transactional
    public RefundResponse create(CreateRefundRequest request, String idempotencyKey) {
        return createRefund(request, idempotencyKey, null);
    }

    @Transactional
    public RefundResponse createForUser(CreateRefundRequest request, String idempotencyKey, long userId) {
        return createRefund(request, idempotencyKey, userId);
    }

    private RefundResponse createRefund(CreateRefundRequest request, String idempotencyKey, Long userId) {
        RefundOrder existing = refundRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getPayment().getOrder().getId().equals(request.orderId())
                    || userId != null && !userId.equals(existing.getPayment().getOrder().getUserId())) {
                throw new ConflictException("Idempotency key was already used for another refund");
            }
            return RefundResponse.from(existing);
        }

        PaymentOrder payment = paymentRepository.findByOrderId(request.orderId())
                .filter(candidate -> userId == null || userId.equals(candidate.getOrder().getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Successful payment for order %d was not found".formatted(request.orderId())));
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Only successful payments can be refunded");
        }
        if (refundRepository.findByPaymentId(payment.getId()).isPresent()) {
            throw new ConflictException("A refund already exists for this payment");
        }

        CustomerOrder order = payment.getOrder();
        RefundOrder refund = refundRepository.save(new RefundOrder(generateRefundNo(), payment, idempotencyKey,
                payment.getAmount(), request.reason().trim(), order.beginRefund()));
        refundRepository.flush();
        metrics.recordCreated();
        return RefundResponse.from(refund);
    }

    @Transactional(readOnly = true)
    public RefundResponse get(String refundNo) { return RefundResponse.from(findRefund(refundNo)); }

    @Transactional(readOnly = true)
    public RefundResponse getForUser(String refundNo, long userId) {
        return RefundResponse.from(refundRepository.findByRefundNoAndPayment_Order_UserId(refundNo, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund %s was not found".formatted(refundNo))));
    }

    @Transactional
    public RefundResponse handleSuccess(String refundNo, RefundSuccessNotification notification) {
        RefundOrder refund = findRefund(refundNo);
        if (notificationAlreadyHandled(refund, notification.notificationId())) return RefundResponse.from(refund);
        if (refund.getStatus() == RefundStatus.SUCCESS) {
            if (!refund.getExternalRefundNo().equals(notification.externalRefundNo().trim())) {
                throw new ConflictException("Refund was already completed by another external refund");
            }
            notificationRepository.save(new RefundNotification(refund, notification.notificationId()));
            return RefundResponse.from(refund);
        }
        notificationRepository.save(new RefundNotification(refund, notification.notificationId()));
        refund.markSuccess(notification.externalRefundNo().trim());
        refund.getPayment().getOrder().markRefunded();
        refundRepository.flush();
        metrics.recordSuccess();
        return RefundResponse.from(refund);
    }

    @Transactional
    public RefundResponse handleFailure(String refundNo, RefundFailureNotification notification) {
        RefundOrder refund = findRefund(refundNo);
        if (notificationAlreadyHandled(refund, notification.notificationId())) return RefundResponse.from(refund);
        notificationRepository.save(new RefundNotification(refund, notification.notificationId()));
        refund.markFailed(notification.reason().trim());
        refund.getPayment().getOrder().restoreAfterRefundFailure(refund.getOrderStatusBeforeRefund());
        refundRepository.flush();
        metrics.recordFailure();
        return RefundResponse.from(refund);
    }

    private boolean notificationAlreadyHandled(RefundOrder refund, String notificationId) {
        RefundNotification existing = notificationRepository.findByNotificationId(notificationId).orElse(null);
        if (existing == null) return false;
        if (!existing.getRefundId().equals(refund.getId())) {
            throw new ConflictException("Notification ID was already used for another refund");
        }
        return true;
    }

    private RefundOrder findRefund(String refundNo) {
        return refundRepository.findByRefundNo(refundNo)
                .orElseThrow(() -> new ResourceNotFoundException("Refund %s was not found".formatted(refundNo)));
    }

    private String generateRefundNo() {
        return "REF" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
