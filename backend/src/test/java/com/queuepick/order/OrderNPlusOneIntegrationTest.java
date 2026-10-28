package com.queuepick.order;

import com.queuepick.order.dto.OrderSummaryDto;
import com.queuepick.product.Product;
import com.queuepick.product.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Testcontainers
@ActiveProfiles("test")
@Transactional
class OrderNPlusOneIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void LAZY_조회에서_N플러스1이_발생한다() {
        // SQL Count = 4

        // Given: 서로 다른 상품 3개와 주문 3개 준비
        for (int i = 1; i <= 3; i++) {

            Product product = productRepository.save(
                    new Product(
                            "테스트 상품 " + i,
                            new BigDecimal("10000.00"),
                            OffsetDateTime.now().minusDays(1)
                    )
            );

            orderRepository.save(
                    new Order(
                            product,
                            1,
                            OffsetDateTime.now()
                    )
            );
        }

        // INSERT SQL을 실행하고 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();

        // Hibernate에서 제공하는 통계 기능
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        // 테스트 시작 전에 statistics.clear()로 통계를 초기화
        statistics.clear();

        // When: 주문 목록 조회
        List<Order> orders = orderRepository.findAll();

        // LAZY 연관 객체에 접근
        // 지연 로딩을 확인하기 위해 @Transactional을 붙임
        List<String> productNames = orders.stream()
                .map(order -> order.getProduct().getName())
                .toList();

        // Then
        long queryCount = statistics.getPrepareStatementCount();

        System.out.println("===== N+1 TEST =====");
        System.out.println("Order Count: " + orders.size());
        System.out.println("SQL Count: " + queryCount);

        assertThat(orders).hasSize(3);
        assertThat(productNames).hasSize(3);

        // 주문 조회 1번 + 상품 조회 3번
        assertThat(queryCount).isEqualTo(4);
    }

    @Test
    void Fetch_Join으로_N플러스1을_해결한다() {
        // SQL Count = 1

        // Given: 서로 다른 상품 3개와 주문 3개 준비
        for (int i = 1; i <= 3; i++) {

            Product product = productRepository.save(
                    new Product(
                            "테스트 상품 " + i,
                            new BigDecimal("10000.00"),
                            OffsetDateTime.now().minusDays(1)
                    )
            );

            orderRepository.save(
                    new Order(
                            product,
                            1,
                            OffsetDateTime.now()
                    )
            );
        }

        // INSERT SQL을 실행하고 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();

        // Hibernate에서 제공하는 통계 기능
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        // 테스트 시작 전에 statistics.clear()로 통계를 초기화
        statistics.clear();

        // When: 주문 목록 조회
        // Fetch Join으로 해결
        List<Order> orders = orderRepository.findAllWithProduct();

        // LAZY 연관 객체에 접근
        // 지연 로딩을 확인하기 위해 @Transactional을 붙임
        List<String> productNames = orders.stream()
                .map(order -> order.getProduct().getName())
                .toList();

        // Then
        long queryCount = statistics.getPrepareStatementCount();

        System.out.println("===== N+1 TEST =====");
        System.out.println("Order Count: " + orders.size());
        System.out.println("SQL Count: " + queryCount);

        assertThat(orders).hasSize(3);
        assertThat(productNames).hasSize(3);

        // 주문 조회 1번 + 상품 조회 3번
        // Fetch Join 적용 후
        assertThat(queryCount).isEqualTo(1);
    }

    @Test
    void EntityGraph로_N플러스1을_해결한다() {
        // SQL Count = 1

        // Given: 서로 다른 상품 3개와 주문 3개 준비
        for (int i = 1; i <= 3; i++) {

            Product product = productRepository.save(
                    new Product(
                            "테스트 상품 " + i,
                            new BigDecimal("10000.00"),
                            OffsetDateTime.now().minusDays(1)
                    )
            );

            orderRepository.save(
                    new Order(
                            product,
                            1,
                            OffsetDateTime.now()
                    )
            );
        }

        // INSERT SQL을 실행하고 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();

        // Hibernate에서 제공하는 통계 기능
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        // 테스트 시작 전에 statistics.clear()로 통계를 초기화
        statistics.clear();

        // When: 주문 목록 조회
        // EntityGraph는 어노테이션
        List<Order> orders = orderRepository.findAllWithProductGraph();

        // LAZY 연관 객체에 접근
        // 지연 로딩을 확인하기 위해 @Transactional을 붙임
        List<String> productNames = orders.stream()
                .map(order -> order.getProduct().getName())
                .toList();

        // Then
        long queryCount = statistics.getPrepareStatementCount();

        System.out.println("===== N+1 TEST =====");
        System.out.println("Order Count: " + orders.size());
        System.out.println("SQL Count: " + queryCount);

        assertThat(orders).hasSize(3);
        assertThat(productNames).hasSize(3);

        // 주문 조회 1번 + 상품 조회 3번
        assertThat(queryCount).isEqualTo(1);
    }

    @Test
    void DTO_Projection으로_필요한_컬럼만_조회한다() {
        // SQL Count = 1

        // Given: 서로 다른 상품 3개와 주문 3개 준비
        for (int i = 1; i <= 3; i++) {

            Product product = productRepository.save(
                    new Product(
                            "테스트 상품 " + i,
                            new BigDecimal("10000.00"),
                            OffsetDateTime.now().minusDays(1)
                    )
            );

            orderRepository.save(
                    new Order(
                            product,
                            1,
                            OffsetDateTime.now()
                    )
            );
        }

        // INSERT SQL을 실행하고 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();

        // Hibernate에서 제공하는 통계 기능
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        // 테스트 시작 전에 statistics.clear()로 통계를 초기화
        statistics.clear();

        // When: 주문 목록 조회
        // DTO Projection
        List<OrderSummaryDto> results =
                orderRepository.findAllOrderSummaries();

        // Then
        long queryCount = statistics.getPrepareStatementCount();

        // DTO Projection
        System.out.println("===== DTO PROJECTION TEST =====");
        System.out.println("Order Count: " + results.size());
        System.out.println("SQL Count: " + queryCount);

        // DTO Projection
        assertThat(results).hasSize(3);
        assertThat(queryCount).isEqualTo(1);
    }
}