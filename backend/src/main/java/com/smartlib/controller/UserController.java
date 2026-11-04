package com.smartlib.controller;

import com.smartlib.dto.CreateLibrarianDto;
import com.smartlib.dto.UpdateProfileDto;
import com.smartlib.dto.UserResponse;
import com.smartlib.service.AuthService;
import com.smartlib.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /** Librarian-only: the old role-switcher source, now used for admin user views. */
    @GetMapping
    @PreAuthorize("hasRole('LIBRARIAN')")
    public List<UserResponse> getUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable String id) {
        return userService.toResponse(userService.getUser(id));
    }

    /** Self-service profile update (students mainly: name + preferred subjects). */
    @PutMapping("/me")
    public UserResponse updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileDto dto) {
        String userId = authentication.getName();
        userService.updateMe(userId, dto);
        return userService.toResponse(userService.getUser(userId));
    }

    /** Librarians cannot self-register; one librarian mints another. */
    @PostMapping("/librarian")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public UserResponse createLibrarian(@Valid @RequestBody CreateLibrarianDto dto) {
        return userService.createLibrarian(dto);
    }
}
