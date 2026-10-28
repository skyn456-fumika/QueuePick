package com.queuepick.order;

import jakarta.persistence.OptimisticLockException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderRetryService {

    // 최초 실행을 포함해 최대 5번 시도한다는 뜻
    // 실제 운영 환경에서는 무조건 재시도해서는 안 됨.
    private static final int MAX_ATTEMPTS = 5;

    private final OrderService orderService;
    private final RetryMetrics retryMetrics;

    public OrderRetryService(
            OrderService orderService,
            RetryMetrics retryMetrics
    ) {
        this.orderService = orderService;
        this.retryMetrics = retryMetrics;
    }

    /**
     * 낙관적 락 충돌이 발생하면 제한된 횟수만큼 재시도한다.
     *
     * 각 시도는 OrderService의 별도 트랜잭션에서 실행한다.
     * 재고 부족이나 잘못된 주문 요청은 재시도하지 않는다.
     */
    public Order createOrderWithRetry(
            Long productId,
            int quantity
    ) {

//        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
//
//            try {
//                return orderService.createOrder(productId, quantity);
//
//            } catch (OptimisticLockingFailureException |
//                     OptimisticLockException e) {
//
//                // 마지막 시도까지 실패했다면 예외를 전달한다.
//                if (attempt == MAX_ATTEMPTS) {
//                    throw e;
//                }
//
//                waitBeforeRetry(attempt);
//            }
//        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            retryMetrics.recordAttempt();

            try {
                return orderService.createOrder(productId, quantity);

            } catch (OptimisticLockingFailureException |
                     OptimisticLockException e) {

                retryMetrics.recordConflict();

                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                waitBeforeRetry(attempt);
            }
        }

        throw new IllegalStateException("주문 재시도 실패");
    }

//    private void waitBeforeRetry(int attempt) {
//
//        // 충돌한 요청들이 다시 동시에 실행되는 것을 완화한다.
//        // 충돌이 반복될수록 대기 시간이 누적되는 구조
//        // 락 처리 자체뿐 아니라 설정한 재시도 대기 시간도 포함
//        long baseDelay = 10L * attempt;
//        long jitter = ThreadLocalRandom.current()
//                .nextLong(1, 11);
//
//        try {
//            Thread.sleep(baseDelay + jitter);
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new IllegalStateException(
//                    "주문 재시도 대기 중 인터럽트 발생", e
//            );
//        }
//    }
    // 요청한 대기 시간이 아니라 실제로 경과한 대기 시간을 측정해
    private void waitBeforeRetry(int attempt) {

        long baseDelay = 10L * attempt;

        long jitter = ThreadLocalRandom.current()
                .nextLong(1, 11);

        long start = System.nanoTime();

        try {
            Thread.sleep(baseDelay + jitter);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "주문 재시도 대기 중 인터럽트 발생", e
            );

        } finally {
            retryMetrics.recordWait(
                    System.nanoTime() - start
            );
        }
    }
}