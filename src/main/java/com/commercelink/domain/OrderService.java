package com.commercelink.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class OrderService {
    private final ProductService productService;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger nextOrderNumber = new AtomicInteger(4);

    public OrderService(ProductService productService) {
        this.productService = productService;
        seed("order-001", "c-42", OrderStatus.PENDING, Map.of("p-101", 1));
        seed("order-002", "c-17", OrderStatus.CONFIRMED, Map.of("p-207", 2));
        seed("order-003", "c-08", OrderStatus.SHIPPED, Map.of("p-103", 1));
    }

    public synchronized Order createOrder(String customerId, Map<String, Integer> quantities) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (quantities == null || quantities.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one product");
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        quantities.forEach((productId, qty) -> {
            if (productId == null || productId.isBlank()) {
                throw new IllegalArgumentException("productId is required for every item");
            }
            if (qty == null || qty < 1) {
                throw new IllegalArgumentException("qty must be a positive integer");
            }
            normalized.merge(productId, qty, Math::addExact);
        });

        productService.reserve(normalized);
        List<OrderLine> lines = normalized.entrySet().stream()
                .map(entry -> {
                    Product product = productService.getProduct(entry.getKey());
                    return new OrderLine(product.id(), product.name(), entry.getValue(), product.price());
                })
                .toList();
        BigDecimal total = lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String orderId = "order-%03d".formatted(nextOrderNumber.getAndIncrement());
        Order order = new Order(orderId, customerId, OrderStatus.PENDING, lines, total, Instant.now());
        orders.put(orderId, order);
        return order;
    }

    public Order getOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("order " + orderId + " not found");
        }
        return order;
    }

    public synchronized Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order current = getOrder(orderId);
        if (newStatus == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (!isAllowed(current.status(), newStatus)) {
            throw new IllegalArgumentException(
                    "cannot change order status from " + current.status() + " to " + newStatus);
        }
        Map<String, Integer> quantities = quantities(current.items());
        if (newStatus == OrderStatus.CANCELLED) {
            productService.release(quantities);
        } else if (newStatus == OrderStatus.SHIPPED) {
            productService.ship(quantities);
        }
        Order updated = new Order(
                current.orderId(), current.customerId(), newStatus, current.items(), current.total(), current.createdAt());
        orders.put(orderId, updated);
        return updated;
    }

    public List<Order> listOrders(OrderStatus status, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return orders.values().stream()
                .filter(order -> status == null || order.status() == status)
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    private void seed(String id, String customerId, OrderStatus status, Map<String, Integer> quantities) {
        if (status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED || status == OrderStatus.SHIPPED) {
            productService.reserve(quantities);
        }
        if (status == OrderStatus.SHIPPED) {
            productService.ship(quantities);
        }
        List<OrderLine> lines = new ArrayList<>();
        quantities.forEach((productId, qty) -> {
            Product product = productService.getProduct(productId);
            lines.add(new OrderLine(productId, product.name(), qty, product.price()));
        });
        BigDecimal total = lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        orders.put(id, new Order(id, customerId, status, lines, total, Instant.now()));
    }

    private boolean isAllowed(OrderStatus current, OrderStatus next) {
        return switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED;
            case SHIPPED, CANCELLED -> false;
        };
    }

    private Map<String, Integer> quantities(List<OrderLine> lines) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        lines.forEach(line -> quantities.merge(line.productId(), line.qty(), Math::addExact));
        return quantities;
    }
}
