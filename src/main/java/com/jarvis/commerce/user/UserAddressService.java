package com.jarvis.commerce.user;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAddressService {

    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;

    public UserAddressService(UserRepository userRepository, UserAddressRepository addressRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional
    public AddressResponse create(long userId, AddressRequest request) {
        User user = findActiveUser(userId);
        boolean makeDefault = request.defaultAddress() || !addressRepository.existsByUserId(userId);
        if (makeDefault) clearCurrentDefault(userId);
        return AddressResponse.from(addressRepository.save(new UserAddress(user.getId(), request, makeDefault)));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(long userId) {
        findUser(userId);
        return addressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(userId)
                .stream().map(AddressResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse get(long userId, long addressId) {
        return AddressResponse.from(findAddress(userId, addressId));
    }

    @Transactional
    public AddressResponse update(long userId, long addressId, AddressRequest request) {
        findActiveUser(userId);
        UserAddress address = findAddress(userId, addressId);
        address.update(request);
        if (request.defaultAddress() && !address.isDefaultAddress()) {
            clearCurrentDefault(userId);
            address.markDefault();
        }
        addressRepository.flush();
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse makeDefault(long userId, long addressId) {
        findActiveUser(userId);
        UserAddress address = findAddress(userId, addressId);
        if (!address.isDefaultAddress()) {
            clearCurrentDefault(userId);
            address.markDefault();
            addressRepository.flush();
        }
        return AddressResponse.from(address);
    }

    @Transactional
    public void delete(long userId, long addressId) {
        findActiveUser(userId);
        UserAddress address = findAddress(userId, addressId);
        boolean deletedDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        addressRepository.flush();
        if (deletedDefault) {
            addressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(userId).stream().findFirst()
                    .ifPresent(UserAddress::markDefault);
        }
    }

    @Transactional(readOnly = true)
    public UserAddress requireUsableAddress(long userId, long addressId) {
        findActiveUser(userId);
        return findAddress(userId, addressId);
    }

    private void clearCurrentDefault(long userId) {
        addressRepository.findByUserIdAndDefaultAddressTrue(userId).ifPresent(UserAddress::clearDefault);
    }

    private User findActiveUser(long userId) {
        User user = findUser(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("Disabled users cannot use addresses or create orders");
        }
        return user;
    }

    private User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User %d was not found".formatted(userId)));
    }

    private UserAddress findAddress(long userId, long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address %d was not found for user %d".formatted(addressId, userId)));
    }
}
