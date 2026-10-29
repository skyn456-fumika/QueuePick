package com.queuepick.common.exception;

public record ErrorResponse(
        String code,
        String message
) {
}