package com.jarvis.commerce.refund;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import com.jarvis.commerce.order.CustomerOrder;
import com.jarvis.commerce.messaging.outbox.OutboxEventService;
import com.jarvis.commerce.payment.PaymentOrder;
import com.jarvis.commerce.payment.PaymentOrderRepository;
import com.jarvis.commerce.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

@Service
public class RefundService {
    private static final List<RefundStatus> RESERVED_STATUSES =
            List.of(RefundStatus.PENDING, RefundStatus.PROCESSING, RefundStatus.SUCCESS);
    private static final List<RefundStatus> ACTIVE_STATUSES =
            List.of(RefundStatus.PENDING, RefundStatus.PROCESSING);
    private final RefundOrderRepository refundRepository;
    private final RefundNotificationRepository notificationRepository;
    private final PaymentOrderRepository paymentRepository;
    private final RefundMetrics metrics;
    private final OutboxEventService outboxService;

    public RefundService(RefundOrderRepository refundRepository,
                         RefundNotificationRepository notificationRepository,
                         PaymentOrderRepository paymentRepository, RefundMetrics metrics,
                         OutboxEventService outboxService) {
        this.refundRepository = refundRepository;
        this.notificationRepository = notificationRepository;
        this.paymentRepository = paymentRepository;
        this.metrics = metrics;
        this.outboxService = outboxService;
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
            if (request.amount() != null && request.amount().compareTo(existing.getAmount()) != 0) {
                throw new ConflictException("Idempotency key was already used with another refund amount");
            }
            return RefundResponse.from(existing);
        }

        PaymentOrder payment = paymentRepository.findLockedByOrderId(request.orderId())
                .filter(candidate -> userId == null || userId.equals(candidate.getOrder().getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Successful payment for order %d was not found".formatted(request.orderId())));
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Only successful payments can be refunded");
        }
        if (refundRepository.existsByPaymentIdAndStatusIn(payment.getId(), ACTIVE_STATUSES)) {
            throw new ConflictException("Another refund is still being processed for this payment");
        }

        BigDecimal reserved = refundRepository.sumAmountByPaymentIdAndStatuses(payment.getId(), RESERVED_STATUSES);
        BigDecimal refundable = payment.getAmount().subtract(reserved);
        BigDecimal requestedAmount = request.amount() == null ? refundable : request.amount();
        if (requestedAmount.signum() <= 0 || requestedAmount.compareTo(refundable) > 0) {
            throw new ConflictException("Refund amount exceeds remaining refundable amount: " + refundable);
        }

        CustomerOrder order = payment.getOrder();
        RefundOrder refund = refundRepository.save(new RefundOrder(generateRefundNo(), payment, idempotencyKey,
                requestedAmount, request.reason().trim(), order.beginRefund()));
        outboxService.requestRefund(refund.getRefundNo());
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

    @Transactional(readOnly = true)
    public List<RefundResponse> listByOrder(long orderId) {
        return refundRepository.findAllByPayment_Order_IdOrderByIdAsc(orderId)
                .stream().map(RefundResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> listByOrderForUser(long orderId, long userId) {
        return refundRepository.findAllByPayment_Order_IdAndPayment_Order_UserIdOrderByIdAsc(orderId, userId)
                .stream().map(RefundResponse::from).toList();
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
        refundRepository.flush();
        BigDecimal refunded = refundRepository.sumAmountByPaymentIdAndStatuses(
                refund.getPayment().getId(), List.of(RefundStatus.SUCCESS));
        if (refunded.compareTo(refund.getPayment().getAmount()) == 0) {
            refund.getPayment().getOrder().markRefunded();
        } else {
            refund.getPayment().getOrder().markPartiallyRefunded(refund.getOrderStatusBeforeRefund());
        }
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

    @Transactional(readOnly = true)
    public RefundSubmission requireSubmission(String refundNo) {
        RefundOrder refund = findRefund(refundNo);
        if (!refund.canSubmit()) return null;
        return new RefundSubmission(refund.getRefundNo(), refund.getPayment().getPaymentNo(),
                refund.getAmount(), refund.getReason());
    }

    @Transactional
    public void markProcessing(String refundNo) {
        RefundOrder refund = findRefund(refundNo);
        refund.markProcessing();
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
