package com.jarvis.commerce.user;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.PageResponse;
import com.jarvis.commerce.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        ensureUnique(username, email, phone, null);
        return UserResponse.from(userRepository.save(new User(username, email, phone)));
    }

    @Transactional(readOnly = true)
    public UserResponse get(long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return PageResponse.from(users, UserResponse::from);
    }

    @Transactional
    public UserResponse update(long id, UpdateUserRequest request) {
        User user = findUser(id);
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        ensureUnique(username, email, phone, id);
        user.updateProfile(username, email, phone);
        userRepository.flush();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse disable(long id) {
        User user = findUser(id);
        user.disable();
        userRepository.flush();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse enable(long id) {
        User user = findUser(id);
        user.enable();
        userRepository.flush();
        return UserResponse.from(user);
    }

    private User findUser(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User %d was not found".formatted(id)));
    }

    private void ensureUnique(String username, String email, String phone, Long excludedId) {
        boolean usernameExists = excludedId == null
                ? userRepository.existsByUsernameIgnoreCase(username)
                : userRepository.existsByUsernameIgnoreCaseAndIdNot(username, excludedId);
        if (usernameExists) throw new ConflictException("Username is already in use");

        boolean emailExists = excludedId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, excludedId);
        if (emailExists) throw new ConflictException("Email is already in use");

        if (phone != null) {
            boolean phoneExists = excludedId == null
                    ? userRepository.existsByPhone(phone)
                    : userRepository.existsByPhoneAndIdNot(phone, excludedId);
            if (phoneExists) throw new ConflictException("Phone is already in use");
        }
    }

    private String normalizeUsername(String username) { return username.trim().toLowerCase(Locale.ROOT); }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
