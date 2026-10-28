package com.queuepick.inventory;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository
        extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    // JPA가 DB의 행 잠금을 획득하는 쿼리를 실행(FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT i
        FROM Inventory i
        WHERE i.product.id = :productId
        """)
    Optional<Inventory> findByProductIdWithLock(
            @Param("productId") Long productId
    );
}