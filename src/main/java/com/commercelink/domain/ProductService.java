package com.commercelink.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProductService {
    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Map<String, Integer> reserved = new ConcurrentHashMap<>();

    public ProductService() {
        add("p-101", "Wireless Mouse", "Accessories", "24.99", 40);
        add("p-102", "Mechanical Keyboard", "Accessories", "89.00", 12);
        add("p-103", "USB-C Hub", "Accessories", "49.50", 18);
        add("p-104", "27-inch Monitor", "Displays", "279.99", 7);
        add("p-105", "Laptop Stand", "Accessories", "39.00", 25);
        add("p-106", "Webcam", "Electronics", "64.95", 14);
        add("p-118", "Noise-Cancelling Headphones", "Electronics", "149.00", 3);
        add("p-207", "Desk Lamp", "Office", "34.50", 20);
    }

    public List<Product> listProducts(String category) {
        return products.values().stream()
                .filter(product -> category == null || product.category().equalsIgnoreCase(category))
                .sorted(Comparator.comparing(Product::id))
                .toList();
    }

    public Product getProduct(String productId) {
        Product product = products.get(productId);
        if (product == null) {
            throw new IllegalArgumentException("product " + productId + " not found");
        }
        return product;
    }

    public synchronized StockLevel checkStock(String productId) {
        Product product = getProduct(productId);
        int reservedQty = reserved.getOrDefault(productId, 0);
        return new StockLevel(productId, product.stockQty(), reservedQty, product.stockQty() - reservedQty);
    }

    synchronized void reserve(Map<String, Integer> quantities) {
        quantities.forEach((productId, qty) -> {
            if (qty < 1) {
                throw new IllegalArgumentException("qty must be a positive integer");
            }
            StockLevel level = checkStock(productId);
            if (level.availableQty() < qty) {
                throw new IllegalArgumentException(
                        "only " + level.availableQty() + " units available of " + productId);
            }
        });
        quantities.forEach((productId, qty) -> reserved.merge(productId, qty, Integer::sum));
    }

    synchronized void release(Map<String, Integer> quantities) {
        quantities.forEach((productId, qty) -> reserved.compute(productId, (key, current) -> {
            int remaining = (current == null ? 0 : current) - qty;
            return remaining <= 0 ? null : remaining;
        }));
    }

    synchronized void ship(Map<String, Integer> quantities) {
        quantities.forEach((productId, qty) -> {
            Product product = getProduct(productId);
            int reservedQty = reserved.getOrDefault(productId, 0);
            if (reservedQty < qty || product.stockQty() < qty) {
                throw new IllegalStateException("inventory reservation is inconsistent for " + productId);
            }
        });
        quantities.forEach((productId, qty) -> {
            Product product = getProduct(productId);
            products.put(productId, new Product(
                    product.id(), product.name(), product.category(), product.price(), product.stockQty() - qty));
            int remaining = reserved.get(productId) - qty;
            if (remaining == 0) {
                reserved.remove(productId);
            } else {
                reserved.put(productId, remaining);
            }
        });
    }

    private void add(String id, String name, String category, String price, int stockQty) {
        products.put(id, new Product(id, name, category, new BigDecimal(price), stockQty));
    }
}
