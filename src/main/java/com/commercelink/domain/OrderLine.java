package com.commercelink.domain;

import java.math.BigDecimal;

public record OrderLine(String productId, String name, int qty, BigDecimal unitPrice) {
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(qty));
    }
}
