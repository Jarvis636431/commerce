package com.jarvis.commerce.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Page<CustomerOrder> findAllByUserId(Long userId, Pageable pageable);
}
