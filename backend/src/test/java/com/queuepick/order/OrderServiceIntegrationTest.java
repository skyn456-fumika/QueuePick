package com.queuepick.order;

import com.queuepick.inventory.Inventory;
import com.queuepick.inventory.InventoryRepository;
import com.queuepick.product.Product;
import com.queuepick.product.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void 정상적으로_주문한다() {

        // Given: 판매 중인 상품 저장
        Product product = new Product(
                "테스트 상품",
                new BigDecimal("10000.00"),
                OffsetDateTime.now().minusDays(1)
        );

        productRepository.save(product);

        // Given: 재고 10개 저장
        Inventory inventory = new Inventory(product, 10);

        inventoryRepository.save(inventory);

        // When: 상품 3개 주문
        Order order = orderService.createOrder(
                product.getId(),
                3
        );

        // Then: DB에서 재고를 다시 조회해 검증
        Inventory updatedInventory = inventoryRepository
                .findByProductId(product.getId())
                .orElseThrow();

        assertThat(updatedInventory.getAvailableQuantity())
                .isEqualTo(7);

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(orderRepository.findAll())
                .hasSize(1);
    }

    @Test
    void 재고가_부족해서_실패한다() {

        // Given: 판매 중인 상품 저장
        Product product = new Product(
                "테스트 상품",
                new BigDecimal("10000.00"),
                OffsetDateTime.now().minusDays(1)
        );

        productRepository.save(product);

        // Given: 재고 10개 저장
        Inventory inventory = new Inventory(product, 2);

        inventoryRepository.save(inventory);

        // When: 상품 3개 주문
        assertThatThrownBy(() ->
                orderService.createOrder(product.getId(), 3)
        )
                // 예외 타입 확인
                .isInstanceOf(IllegalStateException.class)
                // 메시지 확인
                .hasMessage("재고가 부족합니다.");

        assertThat(orderRepository.findAll())
                .isEmpty();

        // 재고가 변경되지 않았는지 확인
        Inventory updatedInventory = inventoryRepository
                .findByProductId(product.getId())
                .orElseThrow();

        assertThat(updatedInventory.getAvailableQuantity())
                .isEqualTo(2);
    }

    @Test
    void 주문_저장에_실패하면_재고_차감도_롤백된다() {

        // Given
        Product product = new Product(
                "롤백 테스트 상품",
                new BigDecimal("10000.00"),
                OffsetDateTime.now().minusDays(1)
        );

        productRepository.save(product);

        Inventory inventory = new Inventory(product, 10);
        inventoryRepository.save(inventory);

        // 주문 저장 시 강제로 예외 발생
        doThrow(new RuntimeException("주문 저장 실패"))
                .when(orderRepository)
                .save(any(Order.class));

        // When & Then
        assertThatThrownBy(() ->
                orderService.createOrder(product.getId(), 3)
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("주문 저장 실패");

        // 트랜잭션 종료 후 DB에서 다시 조회
        Inventory updatedInventory = inventoryRepository
                .findByProductId(product.getId())
                .orElseThrow();

        assertThat(updatedInventory.getAvailableQuantity())
                .isEqualTo(10);
    }

    @Test
    void 재고_UPDATE_이후_예외가_발생해도_롤백된다() {

        // Given
        Product product = new Product(
                "트랜잭션 롤백 테스트",
                new BigDecimal("10000.00"),
                OffsetDateTime.now().minusDays(1)
        );

        productRepository.save(product);

        Inventory inventory = new Inventory(product, 10);
        inventoryRepository.save(inventory);

        /*
         * OrderService가 재고를 차감한 이후 save()를 호출한다.
         *
         * 이 시점에서 flush()를 실행하면
         * 재고 UPDATE SQL이 DB에 전달된다.
         *
         * 이후 예외를 발생시켜 트랜잭션 롤백을 검증한다.
         */
        doAnswer(invocation -> {

            entityManager.flush();

            throw new RuntimeException("강제 트랜잭션 실패");

        }).when(orderRepository).save(any(Order.class));

        // When & Then
        assertThatThrownBy(() ->
                orderService.createOrder(product.getId(), 3)
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("강제 트랜잭션 실패");

        // 트랜잭션 종료 후 DB에서 재고 재조회
        Inventory updatedInventory = inventoryRepository
                .findByProductId(product.getId())
                .orElseThrow();

        assertThat(updatedInventory.getAvailableQuantity())
                .isEqualTo(10);
    }
}