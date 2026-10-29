package com.queuepick.product;

import com.queuepick.product.dto.ProductResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getSaleStartsAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(int page, int size) {

        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1~100이어야 합니다.");
        }

        // 페이지 번호, 페이지 크기, 정렬 조건을 지정
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("id")
                )
        );

        // 조회한 Product 엔티티를
        // ProductResponse DTO로 변환하면서 페이지 정보도 유지
        return productRepository.findAll(pageable)
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getSaleStartsAt()
                ));
    }
}