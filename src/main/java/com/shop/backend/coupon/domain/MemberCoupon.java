package com.shop.backend.coupon.domain;

import com.shop.backend.Member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"member_id", "coupon_id"})})
public class MemberCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    private boolean isUsed;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Builder
    public MemberCoupon(Member member, Coupon coupon, boolean isUsed, LocalDateTime issuedAt) {
        this.member = member;
        this.coupon = coupon;
        this.isUsed = isUsed;
        this.issuedAt = issuedAt;
    }

    public void use(){
        if(this.isUsed) {
            throw new IllegalStateException("이미 사용된 쿠폰 입니다");
        }
        this.isUsed = true;
        }
}
