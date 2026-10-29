package com.queuepick.product;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
// 실제 HTTP 서버를 별도로 실행하지 않고도
// Spring MVC 요청 처리 흐름을 테스트할 수 있도록 MockMvc를 구성
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class ProductControllerIntegrationTest {

    @Container
    // Testcontainers에서 실행한 PostgreSQL의 연결 정보를 Spring Boot에 전달
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 상품이_존재하면_200과_상품정보를_반환한다() throws Exception {

        Product product = productRepository.save(
                new Product(
                        "테스트 상품",
                        new BigDecimal("15000.00"),
                        OffsetDateTime.now().minusDays(1)
                )
        );

        mockMvc.perform(
                        get("/api/products/{productId}", product.getId())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value("테스트 상품"))
                .andExpect(jsonPath("$.price").value(15000.00))
                .andExpect(jsonPath("$.saleStartsAt").exists());
    }

    @Test
    void 상품이_존재하지_않으면_404를_반환한다() throws Exception {

        mockMvc.perform(
                        get("/api/products/{productId}", Long.MAX_VALUE)
                )
                .andExpect(status().isNotFound());
    }

    private void saveProducts(int count) {

        for (int i = 1; i <= count; i++) {

            Product product = new Product(
                    "테스트 상품 " + i,
                    new BigDecimal("10000.00"),
                    OffsetDateTime.now().minusDays(1)
            );

            productRepository.save(product);
        }
    }

    @Test
    void 상품_목록_첫_페이지를_조회한다() throws Exception {

        // Given
        saveProducts(25);

        // When & Then
        mockMvc.perform(
                        get("/api/products")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void 상품_목록_두번째_페이지를_조회한다() throws Exception {

        // Given
        saveProducts(25);

        // When & Then
        mockMvc.perform(
                        get("/api/products")
                                .param("page", "1")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void 상품_목록_마지막_페이지를_조회한다() throws Exception {

        // Given
        saveProducts(25);

        // When & Then
        mockMvc.perform(
                        get("/api/products")
                                .param("page", "2")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void 상품_목록은_ID_내림차순으로_조회된다() throws Exception {

        // Given
        saveProducts(5);

        // When & Then
        mockMvc.perform(
                        get("/api/products")
                                .param("page", "0")
                                .param("size", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name")
                        .value("테스트 상품 5"))
                .andExpect(jsonPath("$.content[4].name")
                        .value("테스트 상품 1"));
    }

    @Test
    void 페이지_크기가_허용범위를_벗어나면_400을_반환한다() throws Exception {

        mockMvc.perform(
                        get("/api/products")
                                .param("page", "0")
                                .param("size", "101")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ARGUMENT"));
    }

    @Test
    void 페이지_번호가_음수이면_400을_반환한다() throws Exception {

        mockMvc.perform(
                        get("/api/products")
                                .param("page", "-1")
                                .param("size", "10")
                )
                .andExpect(status().isBadRequest());
    }
}