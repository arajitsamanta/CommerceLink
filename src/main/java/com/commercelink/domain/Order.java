package com.commercelink.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(
        String orderId,
        String customerId,
        OrderStatus status,
        List<OrderLine> items,
        BigDecimal total,
        Instant createdAt) {
    public Order {
        items = List.copyOf(items);
    }
}
