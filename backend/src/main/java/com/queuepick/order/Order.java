package com.queuepick.order;

import com.queuepick.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    // 주문 순간의 판매 가격을 보존한다. 이후 상품 가격이 변경되어도 과거 주문은 바뀌지 않는다.
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Order() { }

    public Order(Product product, int quantity, OffsetDateTime createdAt) {
        if (product == null) throw new IllegalArgumentException("상품은 필수입니다.");
        if (quantity <= 0) throw new IllegalArgumentException("주문 수량은 양수여야 합니다.");
        if (createdAt == null) throw new IllegalArgumentException("주문 시각은 필수입니다.");
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
        this.status = OrderStatus.PENDING;
        this.createdAt = createdAt;
    }

    public OrderStatus getStatus() { return status; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    public Product getProduct() {
        return product;
    }

    public Long getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

