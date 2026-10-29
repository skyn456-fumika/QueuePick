package com.queuepick.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderResponse(
        Long orderId,
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        String status,
        OffsetDateTime createdAt
) {
}