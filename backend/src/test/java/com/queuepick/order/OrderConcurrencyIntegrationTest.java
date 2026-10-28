package com.queuepick.order;

import com.queuepick.inventory.Inventory;
import com.queuepick.inventory.InventoryRepository;
import com.queuepick.product.Product;
import com.queuepick.product.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderConcurrencyIntegrationTest {

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

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void 재고가_1개일때_동시_주문은_1건만_성공한다()
            throws Exception {

        // Given: 재고 1개인 상품 준비
        Product product = productRepository.save(
                new Product(
                        "한정판 상품",
                        new BigDecimal("10000.00"),
                        OffsetDateTime.now().minusDays(1)
                )
        );

        inventoryRepository.save(new Inventory(product, 1));

        long initialOrderCount = orderRepository.count();

        int threadCount = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        // 두 스레드가 준비될 때까지 기다리는 용도
        CountDownLatch ready = new CountDownLatch(threadCount);

        // 두 스레드를 동시에 출발시키는 용도
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {

                futures.add(executor.submit(() -> {

                    ready.countDown();

                    // 모든 스레드가 준비될 때까지 대기
                    start.await();

                    try {
                        orderService.createOrder(product.getId(), 1);
                        return true;

                    } catch (IllegalStateException |
                             OptimisticLockingFailureException e) {

                        // 재고 부족 또는 낙관적 락 충돌
                        return false;
                    }
                }));
            }

            // 두 스레드가 준비될 때까지 대기
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            // 동시에 주문 시작
            start.countDown();

            int successCount = 0;

            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            // Then: 정확히 하나의 주문만 성공
            assertThat(successCount).isEqualTo(1);

            // 최종 재고 확인
            Inventory updatedInventory = inventoryRepository
                    .findByProductId(product.getId())
                    .orElseThrow();

            assertThat(updatedInventory.getAvailableQuantity())
                    .isZero();

            // 새로운 주문이 정확히 1건 저장됐는지 확인
            assertThat(orderRepository.count() - initialOrderCount)
                    .isEqualTo(1);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 동일한_버전을_수정하면_낙관적_락_충돌이_발생한다()
            throws Exception {

        // Given
        Product product = productRepository.save(
                new Product(
                        "낙관적 락 테스트",
                        new BigDecimal("10000.00"),
                        OffsetDateTime.now().minusDays(1)
                )
        );

        inventoryRepository.saveAndFlush(
                new Inventory(product, 1)
        );

        Long productId = product.getId();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {

                futures.add(executor.submit(() -> {

                    try {
                        transactionTemplate.execute(status -> {

                            // 각 트랜잭션에서 같은 재고 조회
                            Inventory inventory = inventoryRepository
                                    .findByProductId(productId)
                                    .orElseThrow();

                            ready.countDown();

                            try {
                                if (!start.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "시작 신호 대기 시간 초과"
                                    );
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }

                            inventory.decrease(1);

                            // 실제 UPDATE 실행 및 버전 충돌 확인
                            entityManager.flush();

                            return null;
                        });

                        return true;

                    } catch (OptimisticLockingFailureException |
                             OptimisticLockException e) {

                        return false;
                    }
                }));
            }

            // 두 트랜잭션 모두 재고 조회를 완료할 때까지 대기
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            // 두 트랜잭션이 같은 버전을 수정하도록 출발
            start.countDown();

            int successCount = 0;

            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);

            Inventory result = inventoryRepository
                    .findByProductId(productId)
                    .orElseThrow();

            assertThat(result.getAvailableQuantity())
                    .isZero();

            assertThat(result.getVersion())
                    .isEqualTo(1L);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 비관적_락으로_동시_주문시_재고_정합성을_보장한다()
            throws Exception {

        // Given: 재고 1개인 상품 준비
        Product product = productRepository.save(
                new Product(
                        "한정판 상품",
                        new BigDecimal("10000.00"),
                        OffsetDateTime.now().minusDays(1)
                )
        );

        inventoryRepository.save(new Inventory(product, 1));

        long initialOrderCount = orderRepository.count();

        int threadCount = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        // 두 스레드가 준비될 때까지 기다리는 용도
        CountDownLatch ready = new CountDownLatch(threadCount);

        // 두 스레드를 동시에 출발시키는 용도
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {

                futures.add(executor.submit(() -> {

                    ready.countDown();

                    // 모든 스레드가 준비될 때까지 대기
                    start.await();

                    try {
                        orderService.createOrderWithPessimisticLock(
                                product.getId(),
                                1
                        );
                        return true;

                    } catch (IllegalStateException |
                             OptimisticLockingFailureException e) {

                        // 재고 부족 또는 낙관적 락 충돌
                        return false;
                    }
                }));
            }

            // 두 스레드가 준비될 때까지 대기
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            // 동시에 주문 시작
            start.countDown();

            int successCount = 0;

            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            // Then: 정확히 하나의 주문만 성공
            assertThat(successCount).isEqualTo(1);

            // 최종 재고 확인
            Inventory updatedInventory = inventoryRepository
                    .findByProductId(product.getId())
                    .orElseThrow();

            assertThat(updatedInventory.getAvailableQuantity())
                    .isZero();

            // 새로운 주문이 정확히 1건 저장됐는지 확인
            assertThat(orderRepository.count() - initialOrderCount)
                    .isEqualTo(1);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}