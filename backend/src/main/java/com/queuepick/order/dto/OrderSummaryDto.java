package com.queuepick.order.dto;

import java.math.BigDecimal;

public record OrderSummaryDto(
        Long orderId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
}