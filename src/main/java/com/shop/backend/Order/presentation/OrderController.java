package com.shop.backend.Order.presentation;

import com.shop.backend.Order.application.OrderService;
import com.shop.backend.Order.application.dto.request.OrderRequest;
import com.shop.backend.Order.application.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<?> order(@Valid @RequestBody OrderRequest orderRequest){

        log.debug("주문 요청 받음: memberId={}, itemId={}, quantity={}", orderRequest.memberId(), orderRequest.itemId(), orderRequest.quantity());

        orderService.order(orderRequest.memberId(), orderRequest.itemId(), orderRequest.quantity());
        return ResponseEntity.ok().build();
    }

    @GetMapping //주문 조회
    public ResponseEntity<List<OrderResponse>> getOrders(@RequestParam Long memberId) {
        return ResponseEntity.ok(orderService.getOrders(memberId));
    }

    @GetMapping("/{id}") //주문 상세 조회
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @DeleteMapping("/{id}") //주문 취소
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.ok().build();
    }


}
