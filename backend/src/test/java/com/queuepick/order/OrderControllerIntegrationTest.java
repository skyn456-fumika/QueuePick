package com.queuepick.order;

import com.queuepick.inventory.Inventory;
import com.queuepick.inventory.InventoryRepository;
import com.queuepick.product.Product;
import com.queuepick.product.ProductRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class OrderControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void 주문을_생성하면_재고가_차감된다() throws Exception {

        // Given
        Product product = productRepository.save(
                new Product(
                        "테스트 상품",
                        new BigDecimal("15000.00"),
                        OffsetDateTime.now().minusDays(1)
                )
        );

        Inventory inventory = inventoryRepository.save(
                new Inventory(product, 10)
        );

        // When & Then
        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "productId": %d,
                                          "quantity": 3
                                        }
                                        """.formatted(product.getId()))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(product.getId()))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.unitPrice").value(15000.00));

        // 재고 차감 확인
        assertThat(
                inventoryRepository.findByProductId(product.getId())
                        .orElseThrow()
                        .getAvailableQuantity()
        ).isEqualTo(7);

        // 주문 저장 확인
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void 재고가_부족하면_409를_반환한다() throws Exception {

        Product product = productRepository.save(
                new Product(
                        "재고 부족 상품",
                        new BigDecimal("10000.00"),
                        OffsetDateTime.now().minusDays(1)
                )
        );

        inventoryRepository.save(
                new Inventory(product, 2)
        );

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "productId": %d,
                                      "quantity": 3
                                    }
                                    """.formatted(product.getId()))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INSUFFICIENT_STOCK"));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void 주문_수량이_0이면_400을_반환한다() throws Exception {

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "productId": 1,
                                      "quantity": 0
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST"));
    }

    @Test
    void 주문_수량이_없으면_400을_반환한다() throws Exception {

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "productId": 1
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST"));
    }

    @Test
    void 판매_시작_전이면_409를_반환한다() throws Exception {

        Product product = productRepository.save(
                new Product(
                        "판매 예정 상품",
                        new BigDecimal("10000.00"),
                        OffsetDateTime.now().plusDays(1)
                )
        );

        inventoryRepository.save(
                new Inventory(product, 10)
        );

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "productId": %d,
                                      "quantity": 1
                                    }
                                    """.formatted(product.getId()))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SALE_NOT_STARTED"));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void 존재하지_않는_상품을_주문하면_404를_반환한다() throws Exception {

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "productId": 999999,
                                      "quantity": 1
                                    }
                                    """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        assertThat(orderRepository.count()).isZero();
    }
}