package com.queuepick.order;

import com.queuepick.inventory.Inventory;
import com.queuepick.inventory.InventoryRepository;
import com.queuepick.product.Product;
import com.queuepick.product.ProductRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderLockBenchmarkTest {

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
    private OrderRetryService orderRetryService;

    @Autowired
    private RetryMetrics retryMetrics;

    @Test
    void 낙관적_락_성능_측정() throws Exception {
//        runBenchmark(false);
//        runBenchmark(false, false);
        runRepeatedBenchmark(false, false);
    }

    @Test
    void 낙관적_락_재시도_성능_측정() throws Exception {
//        runBenchmark(false, true);
        runRepeatedBenchmark(false, true);
    }

    @Test
    void 비관적_락_성능_측정() throws Exception {
//        runBenchmark(true);
//        runBenchmark(true, false);
        runRepeatedBenchmark(true, false);
    }

//    성능 분석을 시작하기 위한 탐색적 측정
//    private void runBenchmark(boolean pessimistic)
//    낙관적 락에 재시도(Retry)를 추가
//    private void runBenchmark(boolean pessimistic, boolean retry)
//    반복 측정과 워밍업을 추가해 벤치마크를 좀 더 정확하게 만듬
    private BenchmarkResult runBenchmark(
            boolean pessimistic,
            boolean retry)
            throws Exception {

        // 낙관적 락 재시도에 실제로 얼마나 많은 비용이 발생하는지 측정
        // 이전 실행의 재시도 통계 제거
        // 1. 측정값 초기화
        retryMetrics.reset();

        int requestCount = 10;

        Product product = productRepository.save(
                new Product(
                        "성능 테스트 상품",
                        new BigDecimal("10000.00"),
                        OffsetDateTime.now().minusDays(1)
                )
        );

        inventoryRepository.save(
                new Inventory(product, requestCount)
        );

        long initialOrderCount = orderRepository.count();

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch ready =
                new CountDownLatch(requestCount);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<RequestResult>> futures =
                new ArrayList<>();

        try {
            // 2. 주문 작업 제출
            for (int i = 0; i < requestCount; i++) {

                futures.add(executor.submit(() -> {

                    ready.countDown();
                    start.await();

                    long begin = System.nanoTime();

                    try {
//                        if (pessimistic) {
//                            orderService
//                                    .createOrderWithPessimisticLock(
//                                            product.getId(), 1
//                                    );
//                        } else {
//                            orderService.createOrder(
//                                    product.getId(), 1
//                            );
//                        }
                        if (pessimistic) {

                            orderService.createOrderWithPessimisticLock(
                                    product.getId(), 1
                            );

                        } else if (retry) {

                            orderRetryService.createOrderWithRetry(
                                    product.getId(), 1
                            );

                        } else {

                            orderService.createOrder(
                                    product.getId(), 1
                            );
                        }

                        return new RequestResult(
                                true,
                                System.nanoTime() - begin,
                                null
                        );

                    } catch (RuntimeException e) {

                        return new RequestResult(
                                false,
                                System.nanoTime() - begin,
                                e.getClass().getSimpleName()
                        );
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            long totalStart = System.nanoTime();

            // 3. 주문 시작
            start.countDown();

            List<RequestResult> results = new ArrayList<>();

            // 4. 모든 작업 완료 대기
            for (Future<RequestResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            // 5. 모든 작업 완료 후 통계 조회
            RetryMetrics.Snapshot retryStats =
                    retryMetrics.snapshot();

            if (retry) {
                System.out.println("===== RETRY METRICS =====");

                System.out.println(
                        "Total Attempts: " + retryStats.attempts()
                );

                System.out.println(
                        "Lock Conflicts: " + retryStats.conflicts()
                );

                System.out.printf(
                        "Accumulated Retry Wait: %.2f ms%n",
                        retryStats.waitMillis()
                );
            }

            // System.nanoTime()
            // 코드 실행 시간을 측정할 때 사용하는 메서드
            // System.currentTimeMillis()와 달리 경과 시간 측정에 적합
            long totalElapsed =
                    System.nanoTime() - totalStart;

            long successCount = results.stream()
                    .filter(RequestResult::success)
                    .count();

            long failCount = requestCount - successCount;

            List<Long> responseTimes = results.stream()
                    .map(RequestResult::elapsedNanos)
                    .sorted()
                    .toList();

            // P95 계산
            // P95는 응답 시간을 작은 순서대로 정렬했을 때 95번째 백분위수에 해당하는 값
            // nearest-rank 방식으로 계산
            // 다만 요청이 10개뿐이면 P95는 사실상 가장 느린 요청의 응답 시간이 됨.
            // 유의미한 P95를 얻으려면 나중에 충분한 요청 표본을 확보해야 함.
            int p95Index =
                    (int) Math.ceil(responseTimes.size() * 0.95) - 1;

            double p95Ms =
                    responseTimes.get(p95Index) / 1_000_000.0;

            double totalMs =
                    totalElapsed / 1_000_000.0;

            double throughput =
                    requestCount / (totalElapsed / 1_000_000_000.0);

//            System.out.println();
//            System.out.println("===== LOCK BENCHMARK =====");
//            System.out.println(
//                    "Lock: " + (pessimistic ? "PESSIMISTIC" : "OPTIMISTIC")
//            );
//            String lockType = pessimistic
//                    ? "PESSIMISTIC"
//                    : retry
//                    ? "OPTIMISTIC_WITH_RETRY"
//                    : "OPTIMISTIC";
//
//            System.out.println("Lock: " + lockType);
//            System.out.println("Requests: " + requestCount);
//            System.out.println("Success: " + successCount);
//            System.out.println("Failed: " + failCount);
//            System.out.printf("Total: %.2f ms%n", totalMs);
//            System.out.printf("P95: %.2f ms%n", p95Ms);
//            System.out.printf("Throughput: %.2f requests/s%n", throughput);

            results.stream()
                    .filter(result -> !result.success())
                    .map(RequestResult::errorType)
                    .distinct()
                    .forEach(error ->
                            System.out.println("Failure type: " + error)
                    );

            Inventory finalInventory = inventoryRepository
                    .findByProductId(product.getId())
                    .orElseThrow();

            long savedOrderCount =
                    orderRepository.count() - initialOrderCount;

            // 성능 측정과 별도로 데이터 정합성을 검증한다.
            assertThat(savedOrderCount).isEqualTo(successCount);

            assertThat(finalInventory.getAvailableQuantity())
                    .isEqualTo(requestCount - (int) successCount);

            return new BenchmarkResult(
                    successCount,
                    failCount,
                    totalMs,
                    p95Ms,
                    throughput
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private record RequestResult(
            boolean success,
            long elapsedNanos,
            String errorType
    ) {
    }

    private record BenchmarkResult(
            long successCount,
            long failCount,
            double totalMs,
            double p95Ms,
            double throughput
    ) {
    }

    // 반복 실행 메서드
    private void runRepeatedBenchmark(
            boolean pessimistic,
            boolean retry
    ) throws Exception {

        int warmupCount = 2;
        int measurementCount = 7;

        String lockType = pessimistic
                ? "PESSIMISTIC"
                : retry
                ? "OPTIMISTIC_WITH_RETRY"
                : "OPTIMISTIC";

        // Warmup
        for (int i = 0; i < warmupCount; i++) {
            runBenchmark(pessimistic, retry);
        }

        List<BenchmarkResult> results = new ArrayList<>();

        // Measurement
        for (int i = 0; i < measurementCount; i++) {

            BenchmarkResult result =
                    runBenchmark(pessimistic, retry);

            results.add(result);

            System.out.printf(
                    "Run %d | Success: %d | Failed: %d | " +
                            "Total: %.2f ms | P95: %.2f ms%n",
                    i + 1,
                    result.successCount(),
                    result.failCount(),
                    result.totalMs(),
                    result.p95Ms()
            );
        }

        // 전체 소요 시간 기준 정렬
        results.sort(
                Comparator.comparingDouble(BenchmarkResult::totalMs)
        );

        BenchmarkResult median =
                results.get(measurementCount / 2);

        System.out.println();
        System.out.println("===== BENCHMARK SUMMARY =====");
        System.out.println("Lock: " + lockType);
        System.out.println("Measured runs: " + measurementCount);

        System.out.printf(
                "Median total: %.2f ms%n",
                median.totalMs()
        );

        System.out.printf(
                "Median-run P95: %.2f ms%n",
                median.p95Ms()
        );

        System.out.printf(
                "Median-run throughput: %.2f requests/s%n",
                median.throughput()
        );

        System.out.printf(
                "Median-run success: %d%n",
                median.successCount()
        );

        System.out.println("=============================");
    }
}