package com.jarvis.commerce.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundNotificationRepository extends JpaRepository<RefundNotification, Long> {
    Optional<RefundNotification> findByNotificationId(String notificationId);
}
