package com.queuepick.order;

import com.queuepick.order.dto.OrderCreateRequest;
import com.queuepick.order.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            // JSON 요청을 OrderCreateRequest로 변환하고,
            // DTO에 작성한 Bean Validation 제약조건을 검증
            @Valid @RequestBody OrderCreateRequest request
    ) {

        // 비관적 락 적용
        Order order = orderService.createOrderWithPessimisticLock(
                request.productId(),
                request.quantity()
        );

        OrderResponse response = new OrderResponse(
                order.getId(),
                order.getProduct().getId(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getStatus().name(),
                order.getCreatedAt()
        );

        // 새로운 주문을 생성하므로 HTTP 201 Created를 반환
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}