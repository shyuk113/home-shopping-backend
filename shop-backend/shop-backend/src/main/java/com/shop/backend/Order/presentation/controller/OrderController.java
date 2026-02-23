package com.shop.backend.Order.presentation.controller;

import com.shop.backend.Order.application.service.OrderService;
import com.shop.backend.Order.presentation.dto.request.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items/orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<?> order(@RequestBody OrderRequest orderRequest){

        System.out.println("주문 요청 들어옴");

        orderService.order(orderRequest.memberId(), orderRequest.itemId(), orderRequest.quantity());
        return ResponseEntity.ok().build();
    }

}
