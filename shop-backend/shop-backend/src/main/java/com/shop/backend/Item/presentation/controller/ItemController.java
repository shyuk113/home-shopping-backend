package com.shop.backend.Item.presentation.controller;

import com.shop.backend.Item.application.service.ItemService;
import com.shop.backend.Item.presentation.dto.request.ItemDetailDto;
import com.shop.backend.Item.presentation.dto.request.ItemSaveDto;
import com.shop.backend.Item.presentation.dto.request.ItemUpdateDto;
import jakarta.validation.Valid;
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

    @GetMapping("/{id}") //아이템 상세 조회
    public ResponseEntity<ItemDetailDto> getItem(@PathVariable("id") Long id){
        return ResponseEntity.ok(itemService.findItemDetail(id));
    }

    @GetMapping //모든 아이템 조회, 추후 페이징 처리 필요
    public ResponseEntity<List<ItemDetailDto>> getAllItems(){
        return ResponseEntity.ok(itemService.findAllItemDetail());
    }

    @PostMapping //아이템 등록
    public ResponseEntity<Long> createItem(@Valid @RequestBody ItemSaveDto itemSaveDto){
        Long id = itemService.saveItem(itemSaveDto);
        return ResponseEntity.ok(id);
    }

    @PutMapping("/{id}") //아이템 수정
    public ResponseEntity<Void> updateItem(@PathVariable("id") Long itemId, @Valid @RequestBody ItemUpdateDto itemUpdateDto){
        itemService.updateItem(itemId,itemUpdateDto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}") //아이템 삭제
    public ResponseEntity<Void> deleteItem(@PathVariable("id") Long itemId){
        itemService.deleteItem(itemId);
        return ResponseEntity.ok().build();
    }
}
