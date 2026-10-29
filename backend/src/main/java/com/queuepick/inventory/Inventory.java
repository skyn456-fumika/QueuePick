package com.queuepick.inventory;

import com.queuepick.product.Product;
import jakarta.persistence.*;

@Entity
@Table(name = "inventories")
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Version
    private long version;

    protected Inventory() { }

    public Inventory(Product product, int availableQuantity) {
        if (product == null) throw new IllegalArgumentException("상품은 필수입니다.");
        if (availableQuantity < 0) throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        this.product = product;
        this.availableQuantity = availableQuantity;
    }

    /**
     * 수량의 불변식을 도메인에서 지킨다.
     * 동시 수정 충돌은 이 메서드가 아니라 JPA의 flush 시점에 @Version으로 감지된다.
     */
    public void decrease(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("차감 수량은 양수여야 합니다.");
        if (availableQuantity < quantity) {
            throw new InsufficientStockException();
        }
        availableQuantity -= quantity;
    }

    public int getAvailableQuantity() { return availableQuantity; }
    public long getVersion() { return version; }
}

