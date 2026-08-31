package com.jarvis.commerce.order;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.common.PageResponse;
import com.jarvis.commerce.common.ResourceNotFoundException;
import com.jarvis.commerce.inventory.InventoryQuantityRequest;
import com.jarvis.commerce.inventory.InventoryService;
import com.jarvis.commerce.product.ProductStatus;
import com.jarvis.commerce.product.Sku;
import com.jarvis.commerce.product.SkuRepository;
import com.jarvis.commerce.user.UserAddress;
import com.jarvis.commerce.user.UserAddressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final InventoryReservationRepository reservationRepository;
    private final SkuRepository skuRepository;
    private final InventoryService inventoryService;
    private final UserAddressService addressService;

    public OrderService(CustomerOrderRepository orderRepository, OrderItemRepository itemRepository,
                        InventoryReservationRepository reservationRepository, SkuRepository skuRepository,
                        InventoryService inventoryService, UserAddressService addressService) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.reservationRepository = reservationRepository;
        this.skuRepository = skuRepository;
        this.inventoryService = inventoryService;
        this.addressService = addressService;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        UserAddress address = addressService.requireUsableAddress(request.userId(), request.addressId());
        Map<Long, Integer> quantities = aggregateQuantities(request.items());
        List<SkuLine> lines = loadLines(quantities);
        BigDecimal total = lines.stream().map(SkuLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        CustomerOrder order = orderRepository.save(new CustomerOrder(generateOrderNo(), total, request.userId(),
                address.getReceiverName(), address.getPhone(), address.getProvince(), address.getCity(),
                address.getDistrict(), address.getDetailAddress(), address.getPostalCode()));
        List<OrderItem> items = new ArrayList<>();
        List<InventoryReservation> reservations = new ArrayList<>();

        for (SkuLine line : lines) {
            inventoryService.reserve(line.sku().getId(), new InventoryQuantityRequest(line.quantity()));
            items.add(new OrderItem(order, line.sku(), line.quantity()));
            reservations.add(new InventoryReservation(order, line.sku(), line.quantity()));
        }

        itemRepository.saveAll(items);
        reservationRepository.saveAll(reservations);
        orderRepository.flush();
        return OrderResponse.from(order, items);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(long id) {
        CustomerOrder order = findOrder(id);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Pageable pageable) {
        Page<CustomerOrder> orders = orderRepository.findAll(pageable);
        return PageResponse.from(orders, this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listByUser(long userId, Pageable pageable) {
        Page<CustomerOrder> orders = orderRepository.findAllByUserId(userId, pageable);
        return PageResponse.from(orders, this::toResponse);
    }

    @Transactional
    public OrderResponse confirmPayment(long id) {
        CustomerOrder order = findOrder(id);
        order.markPaid();
        for (InventoryReservation reservation : reservations(id)) {
            inventoryService.confirm(reservation.getSkuId(), new InventoryQuantityRequest(reservation.getQuantity()));
            reservation.confirm();
        }
        orderRepository.flush();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(long id) {
        CustomerOrder order = findOrder(id);
        order.cancel();
        for (InventoryReservation reservation : reservations(id)) {
            inventoryService.release(reservation.getSkuId(), new InventoryQuantityRequest(reservation.getQuantity()));
            reservation.release();
        }
        orderRepository.flush();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse complete(long id) {
        CustomerOrder order = findOrder(id);
        order.complete();
        orderRepository.flush();
        return toResponse(order);
    }

    private Map<Long, Integer> aggregateQuantities(List<CreateOrderItemRequest> requestedItems) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (CreateOrderItemRequest item : requestedItems) {
            quantities.merge(item.skuId(), item.quantity(), Math::addExact);
        }
        return quantities;
    }

    private List<SkuLine> loadLines(Map<Long, Integer> quantities) {
        List<SkuLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Sku sku = skuRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("SKU %d was not found".formatted(entry.getKey())));
            if (sku.getProduct().getStatus() != ProductStatus.ON_SALE) {
                throw new ConflictException("SKU %d belongs to a product that is not on sale".formatted(sku.getId()));
            }
            lines.add(new SkuLine(sku, entry.getValue()));
        }
        return lines;
    }

    private CustomerOrder findOrder(long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order %d was not found".formatted(id)));
    }

    private List<InventoryReservation> reservations(long orderId) {
        return reservationRepository.findAllByOrderIdOrderByIdAsc(orderId);
    }

    private OrderResponse toResponse(CustomerOrder order) {
        return OrderResponse.from(order, itemRepository.findAllByOrderIdOrderByIdAsc(order.getId()));
    }

    private String generateOrderNo() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private record SkuLine(Sku sku, int quantity) {
        BigDecimal subtotal() {
            return sku.getPrice().multiply(BigDecimal.valueOf(quantity));
        }
    }
}
