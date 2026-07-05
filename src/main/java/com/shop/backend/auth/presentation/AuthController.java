package com.shop.backend.auth.presentation;

import com.shop.backend.auth.application.dto.LoginRequest;
import com.shop.backend.auth.application.dto.LoginResponse;
import com.shop.backend.auth.application.dto.SignUpRequest;
import com.shop.backend.auth.application.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request){

        String token = authService.login(
            request.getEmail(),
            request.getPassword()
        );

        return ResponseEntity.ok(new LoginResponse(token));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignUpRequest request){
        authService.signup(request.getEmail(),
            request.getPassword(), request.getName(),
            request.getAddress(), request.getPhoneNumber());
        return ResponseEntity.ok().build();
    }
}
