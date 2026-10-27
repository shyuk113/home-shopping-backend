package com.shop.backend.Item.presentation;

import com.shop.backend.Item.application.ItemService;
import com.shop.backend.Item.application.dto.response.ItemResponseDto;
import com.shop.backend.Item.application.dto.request.ItemCreateRequestDto;
import com.shop.backend.Item.application.dto.request.ItemUpdateRequestDto;
import com.shop.backend.Item.domain.ItemCategory;
import com.shop.backend.member.domain.Member;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping("/{id}") //아이템 상세 조회
    public ResponseEntity<ItemResponseDto> getItem(@PathVariable("id") Long id){
        return ResponseEntity.ok(itemService.getItemDetail(id));
    }

    @GetMapping //모든 아이템 조회, 추후 페이징 처리 필요
    public ResponseEntity<List<ItemResponseDto>> getAllItems(){
        return ResponseEntity.ok(itemService.getAllItemDetail());
    }

    @PostMapping //아이템 등록
    public ResponseEntity<ItemResponseDto> createItem(@Valid @RequestBody ItemCreateRequestDto request, @AuthenticationPrincipal Member member){
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.createItem(member.getId(), request));
    }

    @PutMapping("/{id}") //아이템 수정
    public ResponseEntity<Void> updateItem(@PathVariable("id") Long itemId, @Valid @RequestBody ItemUpdateRequestDto itemUpdateDto, @AuthenticationPrincipal Member member){
        itemService.updateItem(itemId,itemUpdateDto, member.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}") //아이템 삭제
    public ResponseEntity<Void> deleteItem(@PathVariable("id") Long itemId, @AuthenticationPrincipal Member member){
        itemService.deleteItem(itemId, member.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/category")
    public ResponseEntity<Page<ItemResponseDto>> getItemsByCategory(@RequestParam ItemCategory category, @PageableDefault Pageable pageable){
        return ResponseEntity.ok(itemService.getItemByCategory(pageable, category));
    }
}
