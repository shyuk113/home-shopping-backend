package com.shop.backend.Cart.presentation.controller;

import com.shop.backend.Cart.application.service.CartService;
import com.shop.backend.Cart.presentation.dto.request.CartAddItemRequestDto;
import com.shop.backend.Cart.presentation.dto.request.CartDeleteItemRequestDto;
import com.shop.backend.Member.domain.model.Member;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private CartService cartService;

    @PostMapping("/{id}")
    public ResponseEntity<?> addToCart(@AuthenticationPrincipal Member member, @RequestBody CartAddItemRequestDto cartAddItemRequestDto) {
        cartService.addToCart(member.getId(), cartAddItemRequestDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<CartAddItemRequestDto>> getCart() {
        List<CartAddItemRequestDto> cartItems = cartService.getAllcartItems();
        return ResponseEntity.ok(cartItems);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<?> clearCartItem(@PathVariable Long itemId) {
        cartService.clearCartItem(itemId);
        return ResponseEntity.ok().build();
    }
}
