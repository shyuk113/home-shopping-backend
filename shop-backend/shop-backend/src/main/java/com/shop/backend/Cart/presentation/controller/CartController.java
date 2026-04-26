package com.shop.backend.Cart.presentation.controller;

import com.shop.backend.Cart.application.service.CartService;
import com.shop.backend.Cart.presentation.dto.request.CartAddItemRequestDto;
import com.shop.backend.Cart.presentation.dto.response.CartItemResponseDto;
import com.shop.backend.Member.domain.model.Member;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CartController {

    private final CartService cartService;

    @PostMapping
    public ResponseEntity<?> addToCart(@AuthenticationPrincipal Member member,@Valid @RequestBody CartAddItemRequestDto cartAddItemRequestDto) {
        cartService.addToCart(member.getId(), cartAddItemRequestDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<CartItemResponseDto>> getCart(@AuthenticationPrincipal Member member) {
        List<CartItemResponseDto> cartItems = cartService.getAllcartItems(member.getId());
        return ResponseEntity.ok(cartItems);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<?> clearCartItem(@PathVariable("itemId") Long itemId,@AuthenticationPrincipal Member member) {
        cartService.clearCartItem(member.getId(), itemId);
        return ResponseEntity.ok().build();
    }
}
