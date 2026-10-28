package com.queuepick.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "sale_starts_at", nullable = false)
    private OffsetDateTime saleStartsAt;

    protected Product() { }

    public Product(String name, BigDecimal price, OffsetDateTime saleStartsAt) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("상품명은 필수입니다.");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        if (saleStartsAt == null) throw new IllegalArgumentException("판매 시작 시각은 필수입니다.");
        this.name = name;
        this.price = price;
        this.saleStartsAt = saleStartsAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public OffsetDateTime getSaleStartsAt() { return saleStartsAt; }
}

