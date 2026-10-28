package com.queuepick.order;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

@Component
public class RetryMetrics {

    // LongAdder는 여러 스레드가 빈번하게 값을 증가시키는 상황에 적합한 카운터
    private final LongAdder attempts = new LongAdder();
    private final LongAdder conflicts = new LongAdder();
    private final LongAdder waitNanos = new LongAdder();

    public void recordAttempt() {
        attempts.increment();
    }

    public void recordConflict() {
        conflicts.increment();
    }

    public void recordWait(long nanos) {
        waitNanos.add(nanos);
    }

    public Snapshot snapshot() {
        // sum()은 다른 스레드가 값을 변경하는 도중에는 원자적인 스냅샷을 보장하지 않음.
        return new Snapshot(
                attempts.sum(),
                conflicts.sum(),
                waitNanos.sum()
        );
    }

    /**
     * 테스트 실행 사이의 통계를 초기화한다.
     * 실행 중인 요청이 없는 상태에서만 호출해야 한다.
     */
    public void reset() {
        attempts.reset();
        conflicts.reset();
        waitNanos.reset();
    }

    public record Snapshot(
            long attempts,
            long conflicts,
            long waitNanos
    ) {
        public double waitMillis() {
            return waitNanos / 1_000_000.0;
        }
    }
}