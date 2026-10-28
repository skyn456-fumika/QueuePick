package com.queuepick.order;

import com.queuepick.product.Product;
import com.queuepick.product.ProductRepository;
import com.queuepick.inventory.Inventory;
import com.queuepick.inventory.InventoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class OrderService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;

    public OrderService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            OrderRepository orderRepository
    ) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 주문 생성과 재고 차감은 하나의 트랜잭션으로 처리한다.
     *
     * 주문 저장에 실패하면 재고 차감도 함께 롤백되어야 한다.
     * 현재는 낙관적 락을 사용하며, 충돌 시 재시도 정책은 추후 설계한다.
     */
    @Transactional
    public Order createOrder(Long productId, int quantity) {

        // 1. 상품 존재 여부 확인
        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("상품이 존재하지 않습니다.")
                );

        // 2. 주문 수량 검증
        if (quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }

        // 3. 판매 시작 시간 확인
        OffsetDateTime now = OffsetDateTime.now();

        if (now.isBefore(product.getSaleStartsAt())) {
            throw new IllegalStateException("아직 판매가 시작되지 않았습니다.");
        }

        // 4. 상품 재고 조회
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("재고 정보가 존재하지 않습니다.")
                );

        // 5. 재고 차감
        inventory.decrease(quantity);

        // 6. 주문 생성
        Order order = new Order(product, quantity, now);

        // 7. 주문 저장
        return orderRepository.save(order);
    }
}