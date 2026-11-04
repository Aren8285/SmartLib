package com.smartlib.service;

import com.smartlib.dto.CreateLibrarianDto;
import com.smartlib.dto.UpdateProfileDto;
import com.smartlib.dto.UserResponse;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public User getUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /** Atomic profile update: name and preferredSubjects via $set, no read-modify-write on the doc. */
    public void updateMe(String userId, UpdateProfileDto dto) {
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update().set("name", dto.getName().trim());
        if (dto.getPreferredSubjects() != null) {
            update.set("preferredSubjects", dto.getPreferredSubjects());
        }
        var result = mongoTemplate.updateFirst(query, update, User.class);
        if (result.getModifiedCount() == 0 && userRepository.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
    }

    /** Librarian-created librarian account; authoritative role assignment is always LIBRARIAN. */
    public UserResponse createLibrarian(CreateLibrarianDto dto) {
        String email = dto.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        User librarian;
        try {
            librarian = userRepository.save(User.builder()
                    .name(dto.getName().trim())
                    .email(email)
                    .role(Role.LIBRARIAN)
                    .points(0)
                    .passwordHash(passwordEncoder.encode(dto.getPassword()))
                    .preferredSubjects(null)
                    .createdAt(Instant.now())
                    .active(true)
                    .build());
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        log.info("Librarian account created: {} (by a librarian)", librarian.getEmail());
        return toResponse(librarian);
    }

    public UserResponse toResponse(User user) {
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
