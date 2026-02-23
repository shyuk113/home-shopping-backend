package com.shop.backend.Auth.dto;

import lombok.Getter;

@Getter
public class SignUpRequest{
    private String email;
    private String password;
    private String name;
    private String address;
    private String phoneNumber;
    }


