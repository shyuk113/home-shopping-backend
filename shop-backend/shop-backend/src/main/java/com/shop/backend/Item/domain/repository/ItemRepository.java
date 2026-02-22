package com.shop.backend.Item.domain.repository;


import com.shop.backend.Item.domain.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

}
