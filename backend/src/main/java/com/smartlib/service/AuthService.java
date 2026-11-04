package com.smartlib.service;

import com.smartlib.config.JwtService;
import com.smartlib.dto.AuthResponse;
import com.smartlib.dto.LoginDto;
import com.smartlib.dto.RegisterDto;
import com.smartlib.dto.UserResponse;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Registers a student. Any client-supplied role is ignored by design. */
    public AuthResponse register(RegisterDto dto) {
        String email = dto.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        User user;
        try {
            user = userRepository.save(User.builder()
                    .name(dto.getName().trim())
                    .email(email)
                    .role(Role.STUDENT)
                    .points(0)
                    .passwordHash(passwordEncoder.encode(dto.getPassword()))
                    .preferredSubjects(dto.getPreferredSubjects() == null ? List.of() : dto.getPreferredSubjects())
                    .createdAt(Instant.now())
                    .active(true)
                    .build());
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        log.info("Registered new student: {} ({})", user.getName(), user.getEmail());
        return buildResponse(user);
    }

    /** Generic failure message on purpose: no user enumeration. */
    public AuthResponse login(LoginDto dto) {
        String email = dto.getEmail().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()
                || !userOpt.get().isActive()
                || !passwordEncoder.matches(dto.getPassword(), userOpt.get().getPasswordHash())) {
            throw new BusinessRuleException("Invalid email or password.");
        }
        return buildResponse(userOpt.get());
    }

    public AuthResponse buildResponse(User user) {
        AuthResponse response = new AuthResponse();
        response.setToken(jwtService.generateToken(user));
        response.setUser(toUserResponse(user));
        return response;
    }

    public UserResponse toUserResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setName(user.getName());
        r.setEmail(user.getEmail());
        r.setRole(user.getRole());
        r.setPoints(user.getPoints());
        r.setPreferredSubjects(user.getPreferredSubjects());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
