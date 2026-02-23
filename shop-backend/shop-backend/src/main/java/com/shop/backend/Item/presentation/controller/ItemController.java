package com.shop.backend.Item.presentation.controller;

import com.shop.backend.Item.application.service.ItemService;
import com.shop.backend.Item.presentation.dto.request.ItemDetailDto;
import com.shop.backend.Item.presentation.dto.request.ItemSaveDto;
import com.shop.backend.Item.presentation.dto.request.ItemUpdateDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping("/{id}")
    public ResponseEntity<ItemDetailDto> getItem(@PathVariable("id") Long id){
        return ResponseEntity.ok(itemService.findItemDetail(id));
    }

    @GetMapping
    public ResponseEntity<List<ItemDetailDto>> getAllItems(){
        return ResponseEntity.ok(itemService.findAllItemDetail());
    }

    @PostMapping
    public ResponseEntity<Long> createItem(@RequestBody ItemSaveDto itemSaveDto){
        Long id = itemService.saveItem(itemSaveDto);
        return ResponseEntity.ok(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateItem(@PathVariable("id") Long itemId,@RequestBody ItemUpdateDto itemUpdateDto){
        itemService.updateItem(itemId,itemUpdateDto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable("id") Long itemId){
        itemService.deleteItem(itemId);
        return ResponseEntity.ok().build();
    }
}
