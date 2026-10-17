package com.commercelink.domain;

public record StockLevel(String productId, int stockQty, int reservedQty, int availableQty) {
}
