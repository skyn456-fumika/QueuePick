package com.queuepick.order;

import com.queuepick.inventory.Inventory;
import com.queuepick.inventory.InventoryRepository;
import com.queuepick.product.Product;
import com.queuepick.product.ProductRepository;

import jakarta.persistence.OptimisticLockException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;

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
class InventoryContentionBenchmarkTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRetryService orderRetryService;

    private static final int REQUEST_COUNT = 10;

    private enum LockStrategy {
        OPTIMISTIC,
        OPTIMISTIC_WITH_RETRY,
        PESSIMISTIC
    }

    @Test
    void 경합_수준에_따른_주문_결과_비교() throws Exception {

//        for (int productCount : List.of(1, 2, 10)) {
//
//            for (LockStrategy strategy : LockStrategy.values()) {
//
//                runScenario(productCount, strategy);
//            }
//        }
        for (int productCount : List.of(1, 2, 10)) {

            for (LockStrategy strategy : LockStrategy.values()) {

                runRepeatedScenario(productCount, strategy);
            }
        }
    }

    // 경합 수준(Contention)에 따라 낙관적 락과 비관적 락의
    // 결과가 어떻게 달라지는지 직접 확인
//    private void runScenario(
//            int productCount,
//            LockStrategy strategy
//    ) throws Exception {
    private ScenarioResult runScenario(
            int productCount,
            LockStrategy strategy
    ) throws Exception {

        int stockPerProduct = REQUEST_COUNT / productCount;

        List<Long> productIds = new ArrayList<>();

        // 각 시나리오마다 독립적인 상품과 재고 생성
        for (int i = 0; i < productCount; i++) {

            Product product = productRepository.save(
                    new Product(
                            "경합 테스트 상품 " + i,
                            new BigDecimal("10000.00"),
                            OffsetDateTime.now().minusDays(1)
                    )
            );

            inventoryRepository.save(
                    new Inventory(product, stockPerProduct)
            );

            productIds.add(product.getId());
        }

        long initialOrderCount = orderRepository.count();

        ExecutorService executor =
                Executors.newFixedThreadPool(REQUEST_COUNT);

        CountDownLatch ready =
                new CountDownLatch(REQUEST_COUNT);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {

                // 요청을 상품에 균등하게 분배
                Long productId = productIds.get(i % productCount);

                futures.add(executor.submit(() -> {

                    ready.countDown();

                    start.await();

                    try {
                        switch (strategy) {

                            case OPTIMISTIC ->
                                    orderService.createOrder(
                                            productId, 1
                                    );

                            case OPTIMISTIC_WITH_RETRY ->
                                    orderRetryService.createOrderWithRetry(
                                            productId, 1
                                    );

                            case PESSIMISTIC ->
                                    orderService
                                            .createOrderWithPessimisticLock(
                                                    productId, 1
                                            );
                        }

                        return true;

                    } catch (OptimisticLockingFailureException |
                             OptimisticLockException e) {

                        return false;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            long begin = System.nanoTime();

            start.countDown();

            int successCount = 0;

            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            double elapsedMs =
                    (System.nanoTime() - begin) / 1_000_000.0;

            int remainingStock = 0;

            for (Long productId : productIds) {

                remainingStock += inventoryRepository
                        .findByProductId(productId)
                        .orElseThrow()
                        .getAvailableQuantity();
            }

            long newOrders =
                    orderRepository.count() - initialOrderCount;

            // 성공 주문 수와 실제 DB 상태의 정합성을 검증
            // 상품이 여러 개이기 때문에 각 상품의 남은 재고를 합산
            assertThat(newOrders).isEqualTo(successCount);

            assertThat(remainingStock)
                    .isEqualTo(REQUEST_COUNT - successCount);

//            System.out.printf(
//                    "Products=%d | Strategy=%s | " +
//                            "Success=%d | Failed=%d | Total=%.2f ms%n",
//                    productCount,
//                    strategy,
//                    successCount,
//                    REQUEST_COUNT - successCount,
//                    elapsedMs
//            );
            return new ScenarioResult(
                    successCount,
                    REQUEST_COUNT - successCount,
                    elapsedMs
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    // 반복 측정 결과를 담을 record
    private record ScenarioResult(
            int successCount,
            int failedCount,
            double elapsedMs
    ) {
    }

    // 반복 측정 메서드
    private void runRepeatedScenario(
            int productCount,
            LockStrategy strategy
    ) throws Exception {

        int warmupCount = 2;
        int measurementCount = 7;

        // Warmup
        for (int i = 0; i < warmupCount; i++) {
            runScenario(productCount, strategy);
        }

        List<ScenarioResult> results = new ArrayList<>();

        // Measurement
        for (int i = 0; i < measurementCount; i++) {

            ScenarioResult result =
                    runScenario(productCount, strategy);

            results.add(result);

            System.out.printf(
                    "Run %d | Products=%d | Strategy=%s | " +
                            "Success=%d | Failed=%d | Total=%.2f ms%n",
                    i + 1,
                    productCount,
                    strategy,
                    result.successCount(),
                    result.failedCount(),
                    result.elapsedMs()
            );
        }

        int totalSuccess = results.stream()
                .mapToInt(ScenarioResult::successCount)
                .sum();

        int totalFailed = results.stream()
                .mapToInt(ScenarioResult::failedCount)
                .sum();

        List<Double> elapsedTimes = results.stream()
                .map(ScenarioResult::elapsedMs)
                .sorted()
                .toList();

        double medianMs =
                elapsedTimes.get(measurementCount / 2);

        double successRate =
                totalSuccess * 100.0
                        / (REQUEST_COUNT * measurementCount);

        System.out.println();
        System.out.println("===== CONTENTION SUMMARY =====");

        System.out.println("Products: " + productCount);
        System.out.println("Strategy: " + strategy);

        System.out.println("Total Success: " + totalSuccess);
        System.out.println("Total Failed: " + totalFailed);

        System.out.printf(
                "Success Rate: %.2f%%%n",
                successRate
        );

        System.out.printf(
                "Median Total: %.2f ms%n",
                medianMs
        );

        System.out.println("==============================");
    }
}