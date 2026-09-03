package com.shop.backend.Item.domain;

import com.shop.backend.common.BaseEntity;
import com.shop.backend.global.exception.OutOfStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private int price;

    private int quantity;

    @Column(length = 1000)
    private String description;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    @Enumerated(EnumType.STRING)
    private ItemCategory category;

    @Builder
    private Item(String name, int price, int quantity, String description, String imageUrl, ItemStatus status, ItemCategory category) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.description = description;
        this.imageUrl = imageUrl;
        this.status = status;
        this.category = category;
    }

    public static Item createItem(String name, int price, int quantity, String description, String imageUrl, ItemStatus status, ItemCategory category) {
        return Item.builder()
                .name(name)
                .price(price)
                .quantity(quantity)
                .description(description)
                .imageUrl(imageUrl)
                .status(status)
                .category(category)
                .build();
    }

    public void removeStock(int quantity){
        int restStock = this.quantity - quantity;
        if(restStock < 0) {
            throw new OutOfStockException("재고가 부족합니다. 현재 남은 재고: " + this.quantity);
        }
        this.quantity = restStock;
        if(this.quantity == 0){
            this.status = ItemStatus.SOLD_OUT;
        }
    }

    public void addStock(int quantity){
        this.quantity += quantity;
    }

    public void update(String name, int price, int quantity,  String description, String imageUrl,  ItemStatus status, ItemCategory category) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.description = description;
        this.imageUrl = imageUrl;
        this.status = status;
        this.category=category;
    }
}
