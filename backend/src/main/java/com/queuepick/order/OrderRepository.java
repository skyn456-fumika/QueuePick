package com.queuepick.order;

import com.queuepick.order.dto.OrderSummaryDto;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Fetch Join
    // Fetch Join은 JPQL에서 조회 계획을 지정
    @Query("""
    SELECT o
    FROM Order o
    JOIN FETCH o.product
    """)
    List<Order> findAllWithProduct();

    // Order를 조회할 때 연관된 Product도 함께 로딩
    // EntityGraph는 어노테이션으로 조회 계획을 지정한다는 차이
    @EntityGraph(attributePaths = {"product"})
    @Query("SELECT o FROM Order o")
    List<Order> findAllWithProductGraph();

    // DTO Projection
    @Query("""
    SELECT new com.queuepick.order.dto.OrderSummaryDto(
        o.id,
        p.name,
        o.quantity,
        o.unitPrice
    )
    FROM Order o
    JOIN o.product p
    """)
    List<OrderSummaryDto> findAllOrderSummaries();

}
