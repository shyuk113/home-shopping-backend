package com.shop.backend.cart.application.service;

import com.shop.backend.cart.domain.Cart;
import com.shop.backend.cart.domain.CartItem;
import com.shop.backend.cart.infrastructure.CartItemRepository;
import com.shop.backend.cart.infrastructure.CartRepository;
import com.shop.backend.cart.application.dto.request.CartAddItemRequestDto;
import com.shop.backend.cart.application.dto.response.CartItemResponseDto;
import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public void addToCart(Long memberId, CartAddItemRequestDto cartAddItemRequestDto) {
        Item item = itemRepository.findById(cartAddItemRequestDto.itemId())
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다"));
        Member member = memberRepository.findById(memberId).orElseThrow(()->
        new EntityNotFoundException("존재하지 않는 회원입니다."));

        Cart cart = cartRepository.findByMemberId(memberId).orElseGet(() ->
        {Cart newCart = new Cart();
        newCart.setMember(member);
        return cartRepository.save(newCart);
        });

        Optional<CartItem> existingItem = cartItemRepository.findByCartAndItem(cart,item);

        if (existingItem.isPresent()){
            CartItem cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + cartAddItemRequestDto.quantity());
        } else {
            CartItem cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setItem(item);
            cartItem.setQuantity(cartAddItemRequestDto.quantity());
            cartItemRepository.save(cartItem);
        }
    }


    @Transactional
    public List<CartItemResponseDto> getAllcartItems(Long memberId) {
        return cartItemRepository.findByCart_Member_Id(memberId).stream()
            .map(CartItemResponseDto::from)
            .toList();
    }

    @Transactional
    public void clearCartItem(Long memberId, Long itemId){
        CartItem cartItem = cartItemRepository.findByItem_IdAndCart_Member_Id(itemId, memberId)
            .orElseThrow(()-> new EntityNotFoundException("장바구니에 존재하지 않는 상품입니다"));
        cartItemRepository.delete(cartItem);
    }
}
