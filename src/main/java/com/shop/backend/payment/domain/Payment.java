package com.shop.backend.payment.domain;

import com.shop.backend.common.BaseEntity;
import com.shop.backend.order.domain.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable =false)
    private Order order;

    @Column(nullable = false, unique = true)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private int amount;

    private String pgProvider;

    private String failReason;

    private LocalDateTime approvedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Payment(Order order, String paymentKey, PaymentMethod method, int amount, String pgProvider, PaymentStatus status){
        this.order = order;
        this.paymentKey = paymentKey;
        this.method = method;
        this.amount = amount;
        this.pgProvider = pgProvider;
        this.status = PaymentStatus.READY;
    }

    public static Payment createPayment(Order order, String paymentKey, PaymentMethod method, int amount, String pgProvider, PaymentStatus status){
        return Payment.builder()
                .order(order)
                .paymentKey(paymentKey)
                .method(method)
                .amount(amount)
                .pgProvider(pgProvider)
                .status(status)
                .build();
    }

    public void approve(LocalDateTime approvedAt){
        if(this.status == PaymentStatus.DONE){
            return;
        }
        this.status = PaymentStatus.DONE;
        this.approvedAt = approvedAt;
    }

    public void fail(String reason){
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }

    public void cancel(){
        if(this.status != PaymentStatus.DONE){
            throw new IllegalStateException("완료된 결제만 취소할 수 있습니다.");
        }
        this.status = PaymentStatus.CANCELED;
    }
}
