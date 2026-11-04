package com.smartlib.controller;

import com.smartlib.dto.AuthResponse;
import com.smartlib.dto.LoginDto;
import com.smartlib.dto.RegisterDto;
import com.smartlib.dto.UserResponse;
import com.smartlib.service.AuthService;
import com.smartlib.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.toResponse(userService.getUser(authentication.getName()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterDto dto) {
        return authService.register(dto);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginDto dto) {
        return authService.login(dto);
    }
}
