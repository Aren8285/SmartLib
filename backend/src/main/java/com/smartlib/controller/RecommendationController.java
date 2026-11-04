package com.smartlib.controller;

import com.smartlib.dto.RecommendationDto;
import com.smartlib.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public List<RecommendationDto> forMe(Authentication authentication) {
        return recommendationService.recommendFor(authentication.getName());
    }
}
