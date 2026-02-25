package com.shop.backend.Cart.application.service;

import static java.util.stream.Collectors.toList;

import com.shop.backend.Cart.domain.model.Cart;
import com.shop.backend.Cart.domain.model.CartItem;
import com.shop.backend.Cart.domain.repository.CartItemRepository;
import com.shop.backend.Cart.domain.repository.CartRepository;
import com.shop.backend.Cart.presentation.dto.request.CartAddItemRequestDto;
import com.shop.backend.Item.domain.model.Item;
import com.shop.backend.Item.domain.repository.ItemRepository;
import com.shop.backend.Member.domain.model.Member;
import com.shop.backend.Member.domain.repository.MemberRepository;
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
            .orElseThrow(() -> new RuntimeException("존재하지 않는 상품입니다"));
        Member member = memberRepository.findById(memberId).orElseThrow();

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
    public List<CartAddItemRequestDto> getAllcartItems(Long memberId) {
        return cartItemRepository.findByCart_Member_Id(memberId).stream()
            .map(cartItem ->
                new CartAddItemRequestDto(
                    cartItem.getItem().getId(),
                    cartItem.getQuantity()
                ))
            .toList();
    }

    @Transactional
    public void clearCartItem(Long memberId, Long itemId){
        CartItem cartItem = cartItemRepository.findByItem_IdAndCart_Member_Id(itemId, memberId)
            .orElseThrow(()-> new RuntimeException("장바구니에 존재하지 않는 상품입니다"));
        cartItemRepository.delete(cartItem);
    }
}
