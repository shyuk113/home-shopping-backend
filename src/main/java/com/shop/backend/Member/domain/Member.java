package com.shop.backend.Member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role{
        USER, ADMIN
    }

    @Column(length = 100)
    private String address;

    @Builder
    public Member(String name, String phone, String email,String password, Role role, String address){
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.password = password;
        this.role = role;
        this.address = address;
    }

    public void update(String name, String phone,String address){
        this.name = name;
        this.phone = phone;
        this.address = address;
    }


}
