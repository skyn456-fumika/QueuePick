package com.queuepick.inventory;

import com.queuepick.product.Product;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InventoryTest {
    private final Product product = new Product("테스트 상품", new BigDecimal("10000.00"), OffsetDateTime.now());

    @Test
    void 재고를_정상적으로_차감한다() {
        Inventory inventory = new Inventory(product, 10);
        inventory.decrease(3);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(7);
    }

    @Test
    void 재고보다_많은_수량은_차감할_수_없다() {
        Inventory inventory = new Inventory(product, 2);
        assertThatThrownBy(() -> inventory.decrease(3))
            .isInstanceOf(IllegalStateException.class);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(2);
    }

    @Test
    void 음수나_영은_차감할_수_없다() {
        Inventory inventory = new Inventory(product, 2);
        assertThatThrownBy(() -> inventory.decrease(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

