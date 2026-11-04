package com.smartlib.dto;

import lombok.Data;

/** A book recommendation with a human-readable reason and its score. */
@Data
public class RecommendationDto {
    private BookResponse book;
    private String reason;
    private double score;
}
