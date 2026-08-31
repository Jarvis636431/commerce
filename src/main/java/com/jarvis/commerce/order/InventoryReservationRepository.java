package com.jarvis.commerce.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findAllByOrderIdOrderByIdAsc(Long orderId);
}
