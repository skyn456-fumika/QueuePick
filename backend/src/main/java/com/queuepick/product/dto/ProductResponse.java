package com.queuepick.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        OffsetDateTime saleStartsAt
) {
}