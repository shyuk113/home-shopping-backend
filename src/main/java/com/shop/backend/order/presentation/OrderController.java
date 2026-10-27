package com.shop.backend.order.presentation;

import com.shop.backend.member.domain.Member;
import com.shop.backend.order.application.OrderService;
import com.shop.backend.order.application.dto.request.OrderRequest;
import com.shop.backend.order.application.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping //주문 요청
    public ResponseEntity<?> order(@Valid @RequestBody OrderRequest orderRequest, @AuthenticationPrincipal Member member){

        log.debug("주문 요청 받음: memberId={}, itemId={}, quantity={}", member.getId(), orderRequest.itemId(), orderRequest.quantity());

        orderService.order(member.getId(), orderRequest.itemId(), orderRequest.quantity());
        return ResponseEntity.ok().build();
    }

    @GetMapping //주문 조회
    public ResponseEntity<List<OrderResponse>> getOrders(@AuthenticationPrincipal Member member) {
        return ResponseEntity.ok(orderService.getOrders(member.getId()));
    }

    @GetMapping("/{id}") //주문 상세 조회
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id, @AuthenticationPrincipal Member member) {
        return ResponseEntity.ok(orderService.getOrder(id, member.getId()));
    }

    @DeleteMapping("/{id}") //주문 취소
    public ResponseEntity<Void> cancel(@PathVariable Long id, @AuthenticationPrincipal Member member) {
        orderService.cancelOrder(id, member.getId());
        return ResponseEntity.ok().build();
    }


}
