package com.jarvis.commerce.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, long id);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, long id);
    boolean existsByPhoneAndIdNot(String phone, long id);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
}
