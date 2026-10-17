package com.commercelink.domain;

import java.math.BigDecimal;

public record Product(String id, String name, String category, BigDecimal price, int stockQty) {
}
