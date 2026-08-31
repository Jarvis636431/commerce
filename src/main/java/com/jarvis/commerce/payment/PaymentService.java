package com.jarvis.commerce.payment;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import com.jarvis.commerce.order.CustomerOrder;
import com.jarvis.commerce.order.CustomerOrderRepository;
import com.jarvis.commerce.order.OrderService;
import com.jarvis.commerce.order.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentOrderRepository paymentRepository;
    private final PaymentNotificationRepository notificationRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock clock;
    private final Duration paymentTimeout;

    public PaymentService(PaymentOrderRepository paymentRepository,
                          PaymentNotificationRepository notificationRepository,
                          CustomerOrderRepository orderRepository,
                          OrderService orderService,
                          Clock clock,
                          @Value("${commerce.payment.timeout:PT15M}") Duration paymentTimeout) {
        this.paymentRepository = paymentRepository;
        this.notificationRepository = notificationRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.clock = clock;
        this.paymentTimeout = paymentTimeout;
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request, String idempotencyKey) {
        PaymentOrder existing = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getOrder().getId().equals(request.orderId())) {
                throw new ConflictException("Idempotency key was already used for another order");
            }
            return PaymentResponse.from(existing);
        }

        if (paymentRepository.findByOrderId(request.orderId()).isPresent()) {
            throw new ConflictException("A payment already exists for order %d".formatted(request.orderId()));
        }
        CustomerOrder order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order %d was not found".formatted(request.orderId())));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("Only pending orders can create a payment");
        }

        PaymentOrder payment = paymentRepository.save(new PaymentOrder(
                generatePaymentNo(), order, idempotencyKey, order.getTotalAmount(),
                OffsetDateTime.now(clock).plus(paymentTimeout)));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(String paymentNo) {
        return PaymentResponse.from(findPayment(paymentNo));
    }

    @Transactional
    public PaymentResponse handleSuccess(String paymentNo, PaymentSuccessNotification notification) {
        PaymentOrder payment = findPayment(paymentNo);
        if (notificationAlreadyHandled(payment, notification.notificationId())) {
            return PaymentResponse.from(payment);
        }
        if (payment.getAmount().compareTo(notification.amount()) != 0) {
            throw new ConflictException("Paid amount does not match the payment amount");
        }
        if (payment.getStatus() == PaymentStatus.PENDING
                && payment.isExpiredAt(OffsetDateTime.now(clock))) {
            throw new ConflictException("Payment has expired and cannot be completed");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (!payment.getExternalTransactionNo().equals(notification.externalTransactionNo().trim())) {
                throw new ConflictException("Payment was already completed by another external transaction");
            }
            notificationRepository.save(new PaymentNotification(payment, notification.notificationId()));
            return PaymentResponse.from(payment);
        }
        notificationRepository.save(new PaymentNotification(payment, notification.notificationId()));
        payment.markSuccess(notification.externalTransactionNo().trim());
        orderService.confirmPayment(payment.getOrder().getId());
        paymentRepository.flush();
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse handleFailure(String paymentNo, PaymentFailureNotification notification) {
        PaymentOrder payment = findPayment(paymentNo);
        if (notificationAlreadyHandled(payment, notification.notificationId())) {
            return PaymentResponse.from(payment);
        }
        notificationRepository.save(new PaymentNotification(payment, notification.notificationId()));
        payment.markFailed(notification.reason().trim());
        paymentRepository.flush();
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse close(String paymentNo) {
        PaymentOrder payment = findPayment(paymentNo);
        payment.close();
        orderService.cancel(payment.getOrder().getId());
        paymentRepository.flush();
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse retry(String paymentNo) {
        PaymentOrder payment = findPayment(paymentNo);
        if (payment.getOrder().getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("Payment cannot be retried because the order is no longer pending");
        }
        payment.retry(OffsetDateTime.now(clock).plus(paymentTimeout));
        paymentRepository.flush();
        return PaymentResponse.from(payment);
    }

    @Transactional
    public boolean expireIfDue(String paymentNo, OffsetDateTime now) {
        PaymentOrder payment = findPayment(paymentNo);
        if (payment.getStatus() != PaymentStatus.PENDING || !payment.isExpiredAt(now)) {
            return false;
        }
        payment.close();
        orderService.cancel(payment.getOrder().getId());
        paymentRepository.flush();
        return true;
    }

    private PaymentOrder findPayment(String paymentNo) {
        return paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new ResourceNotFoundException("Payment %s was not found".formatted(paymentNo)));
    }

    private boolean notificationAlreadyHandled(PaymentOrder payment, String notificationId) {
        PaymentNotification existing = notificationRepository.findByNotificationId(notificationId).orElse(null);
        if (existing == null) {
            return false;
        }
        if (!existing.getPaymentId().equals(payment.getId())) {
            throw new ConflictException("Notification ID was already used for another payment");
        }
        return true;
    }

    private String generatePaymentNo() {
        return "PAY" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
