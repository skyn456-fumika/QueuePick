package com.queuepick.order;

public class SaleNotStartedException extends RuntimeException {

    public SaleNotStartedException() {
        super("아직 판매가 시작되지 않았습니다.");
    }
}