package com.jarvis.commerce.cart;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.ResourceNotFoundException;
import com.jarvis.commerce.inventory.Inventory;
import com.jarvis.commerce.inventory.InventoryRepository;
import com.jarvis.commerce.product.ProductStatus;
import com.jarvis.commerce.product.Sku;
import com.jarvis.commerce.product.SkuRepository;
import com.jarvis.commerce.user.User;
import com.jarvis.commerce.user.UserRepository;
import com.jarvis.commerce.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final int MAXIMUM_QUANTITY = 99;

    private final CartStore cartStore;
    private final UserRepository userRepository;
    private final SkuRepository skuRepository;
    private final InventoryRepository inventoryRepository;

    public CartService(CartStore cartStore, UserRepository userRepository, SkuRepository skuRepository,
                       InventoryRepository inventoryRepository) {
        this.cartStore = cartStore;
        this.userRepository = userRepository;
        this.skuRepository = skuRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse get(long userId) {
        requireActiveUser(userId);
        return buildResponse(userId, cartStore.getItems(userId));
    }

    @Transactional(readOnly = true)
    public CartResponse add(long userId, AddCartItemRequest request) {
        requireActiveUser(userId);
        Sku sku = requirePurchasableSku(request.skuId());
        int quantity = cartStore.increment(userId, sku.getId(), request.quantity(), MAXIMUM_QUANTITY);
        if (quantity < 0) throw new ConflictException("Cart item quantity cannot exceed 99");
        return buildResponse(userId, cartStore.getItems(userId));
    }

    @Transactional(readOnly = true)
    public CartResponse update(long userId, long skuId, UpdateCartItemRequest request) {
        requireActiveUser(userId);
        requirePurchasableSku(skuId);
        if (!cartStore.getItems(userId).containsKey(skuId)) {
            throw new ResourceNotFoundException("SKU %d was not found in the cart".formatted(skuId));
        }
        cartStore.put(userId, skuId, request.quantity());
        return buildResponse(userId, cartStore.getItems(userId));
    }

    @Transactional(readOnly = true)
    public void remove(long userId, long skuId) {
        requireActiveUser(userId);
        cartStore.remove(userId, skuId);
    }

    @Transactional(readOnly = true)
    public void clear(long userId) {
        requireActiveUser(userId);
        cartStore.clear(userId);
    }

    private CartResponse buildResponse(long userId, Map<Long, Integer> storedItems) {
        if (storedItems.isEmpty()) return new CartResponse(userId, 0, 0, BigDecimal.ZERO, List.of());

        Map<Long, Sku> skus = skuRepository.findAllById(storedItems.keySet()).stream()
                .collect(Collectors.toMap(Sku::getId, Function.identity()));
        Map<Long, Inventory> inventories = inventoryRepository.findAllBySkuIdIn(storedItems.keySet()).stream()
                .collect(Collectors.toMap(inventory -> inventory.getSku().getId(), Function.identity()));
        List<CartItemResponse> items = new ArrayList<>();

        storedItems.forEach((skuId, quantity) -> {
            Sku sku = skus.get(skuId);
            if (sku == null) {
                items.add(new CartItemResponse(skuId, null, null, null, null, quantity,
                        null, null, null, false));
                return;
            }
            Inventory inventory = inventories.get(skuId);
            Integer available = inventory == null ? null : inventory.getAvailableQuantity();
            boolean purchasable = sku.getProduct().getStatus() == ProductStatus.ON_SALE
                    && available != null && available >= quantity;
            items.add(new CartItemResponse(skuId, sku.getProduct().getId(), sku.getCode(), sku.getName(),
                    sku.getPrice(), quantity, sku.getPrice().multiply(BigDecimal.valueOf(quantity)),
                    sku.getProduct().getStatus(), available, purchasable));
        });
        items.sort(Comparator.comparing(CartItemResponse::skuId));
        BigDecimal total = items.stream().filter(CartItemResponse::purchasable)
                .map(CartItemResponse::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = storedItems.values().stream().mapToInt(Integer::intValue).sum();
        return new CartResponse(userId, items.size(), totalQuantity, total, items);
    }

    private User requireActiveUser(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User %d was not found".formatted(userId)));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("Disabled users cannot use a shopping cart");
        }
        return user;
    }

    private Sku requirePurchasableSku(long skuId) {
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("SKU %d was not found".formatted(skuId)));
        if (sku.getProduct().getStatus() != ProductStatus.ON_SALE) {
            throw new ConflictException("Only on-sale products can be added to the cart");
        }
        return sku;
    }
}
