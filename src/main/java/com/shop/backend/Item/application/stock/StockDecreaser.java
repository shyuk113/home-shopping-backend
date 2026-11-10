package com.shop.backend.Item.application.stock;

public interface StockDecreaser {
    boolean decrease(Long itemId, int quantity);
}
