package com.queuepick.performance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
// JPA가 아닌 JdbcTemplate을 사용
// JPA 조회 최적화가 아니라 PostgreSQL 자체의 실행 계획을 분석하는 실험
class PostgresIndexBenchmarkTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 인덱스_적용_전후_실행계획을_비교한다() {

        // 1. 테스트 상품 생성
        Long productId = jdbcTemplate.queryForObject(
                """
                INSERT INTO products (
                    name,
                    price,
                    sale_starts_at
                )
                VALUES (
                    '인덱스 테스트 상품',
                    10000.00,
                    now()
                )
                RETURNING id
                """,
                Long.class
        );

        // 2. 주문 10만 건 생성
        // generate_series()
        // PostgreSQL에서 1부터 100,000까지 숫자를 생성하는 기능
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    product_id,
                    quantity,
                    unit_price,
                    status,
                    created_at
                )
                SELECT
                    ?,
                    1,
                    10000.00,
                    'CONFIRMED',
                    now() - (n * INTERVAL '1 second')
                FROM generate_series(1, 100000) AS n
                """,
                productId
        );

        // 3. PostgreSQL 통계 갱신
        // PostgreSQL에 테이블의 데이터 분포와 통계를 수집하도록 요청하는 명령어
        jdbcTemplate.execute("ANALYZE orders");

        // 4. 인덱스 적용 전 실행 계획
        System.out.println("===== BEFORE INDEX =====");

        printExplain();
        benchmarkIndex("BEFORE INDEX");

        // 5. 인덱스 생성
        jdbcTemplate.execute(
                """
                CREATE INDEX idx_orders_created_at
                ON orders (created_at DESC)
                """
        );

        // 6. 통계 갱신
        jdbcTemplate.execute("ANALYZE orders");

        // 7. 인덱스 적용 후 실행 계획
        System.out.println("===== AFTER INDEX =====");

        printExplain();
        benchmarkIndex("AFTER INDEX");

        // 좁은 범위: 최근 1시간
        printRangeExplain("LAST 1 HOUR", "3600");

        // 넓은 범위: 최근 1일
        printRangeExplain("LAST 1 DAY", "86400");
    }

    private void printExplain() {

        // EXPLAIN: PostgreSQL이 예상한 실행 계획
        // ANALYZE 옵션: SQL을 실제 실행하고 측정한 결과
        // BUFFERS 옵션: 데이터 페이지를 읽거나 캐시에서 사용한 정보
        List<String> results = jdbcTemplate.queryForList(
                """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT *
                FROM orders
                ORDER BY created_at DESC
                LIMIT 20
                """,
                String.class
        );

        results.forEach(System.out::println);
    }

    private void printRangeExplain(String label, String interval) {

        System.out.println("===== " + label + " =====");

        List<String> results = jdbcTemplate.queryForList(
                """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT *
                FROM orders
                WHERE created_at >= now() - (? * INTERVAL '1 second')
                """,
                String.class,
                Integer.parseInt(interval)
        );

        results.forEach(System.out::println);
    }

    // 반복 측정 메서드
    private void benchmarkIndex(String label) {

        String sql = """
            EXPLAIN (ANALYZE, BUFFERS)
            SELECT *
            FROM orders
            ORDER BY created_at DESC
            LIMIT 20
            """;

        // 워밍업 3회: 측정 결과에서 제외
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.queryForList(sql, String.class);
        }

        List<Double> executionTimes = new ArrayList<>();

        // 실제 측정 7회
        for (int i = 1; i <= 7; i++) {

            List<String> explain =
                    jdbcTemplate.queryForList(sql, String.class);

            double executionTime = explain.stream()
                    .filter(line -> line.startsWith("Execution Time:"))
                    .map(line -> line
                            .replace("Execution Time:", "")
                            .replace("ms", "")
                            .trim())
                    .mapToDouble(Double::parseDouble)
                    .findFirst()
                    .orElseThrow();

            executionTimes.add(executionTime);

            System.out.printf(
                    "Run %d | Execution Time: %.3f ms%n",
                    i,
                    executionTime
            );
        }

        executionTimes.sort(Double::compareTo);

        double median = executionTimes.get(3);

        System.out.println("===== " + label + " SUMMARY =====");
        System.out.printf("Median: %.3f ms%n", median);
    }
}