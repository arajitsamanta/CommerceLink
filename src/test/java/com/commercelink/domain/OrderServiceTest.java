package com.commercelink.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {
    @Test
    void reservesStockAndReleasesItWhenOrderIsCancelled() {
        ProductService products = new ProductService();
        OrderService orders = new OrderService(products);

        Order order = orders.createOrder("c-test", Map.of("p-101", 2));
        assertEquals(37, products.checkStock("p-101").availableQty());

        orders.updateOrderStatus(order.orderId(), OrderStatus.CANCELLED);
        assertEquals(39, products.checkStock("p-101").availableQty());
    }

    @Test
    void rejectsInsufficientStockWithoutCreatingAnOrder() {
        ProductService products = new ProductService();
        OrderService orders = new OrderService(products);

        assertThrows(IllegalArgumentException.class,
                () -> orders.createOrder("c-test", Map.of("p-118", 500)));
        assertEquals(3, products.checkStock("p-118").availableQty());
        assertEquals(3, orders.listOrders(null, 20).size());
    }

    @Test
    void rejectsIllegalTransitionsAndConsumesReservedStockOnShipment() {
        ProductService products = new ProductService();
        OrderService orders = new OrderService(products);
        Order created = orders.createOrder("c-test", Map.of("p-101", 2));
        orders.updateOrderStatus(created.orderId(), OrderStatus.CONFIRMED);
        orders.updateOrderStatus(created.orderId(), OrderStatus.SHIPPED);

        assertEquals(38, products.checkStock("p-101").stockQty());
        assertEquals(37, products.checkStock("p-101").availableQty());
        assertThrows(IllegalArgumentException.class,
                () -> orders.updateOrderStatus(created.orderId(), OrderStatus.PENDING));
    }
}
