package com.jarvis.commerce.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findAllByUserIdOrderByDefaultAddressDescIdDesc(long userId);
    Optional<UserAddress> findByIdAndUserId(long id, long userId);
    Optional<UserAddress> findByUserIdAndDefaultAddressTrue(long userId);
    boolean existsByUserId(long userId);
}
