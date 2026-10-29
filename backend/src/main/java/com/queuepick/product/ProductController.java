package com.queuepick.product;

import com.queuepick.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // REST API
    @GetMapping("/{productId}")
    public ProductResponse getProduct(
            @PathVariable Long productId
    ) {
        return productService.getProduct(productId);
    }

    // 상품 목록 페이징 API
    @GetMapping
    public Page<ProductResponse> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return productService.getProducts(page, size);
    }
}