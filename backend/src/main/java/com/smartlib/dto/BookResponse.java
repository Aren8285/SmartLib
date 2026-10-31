package com.smartlib.dto;

import lombok.Data;

@Data
public class BookResponse {
    private String id;
    private String title;
    private String author;
    private String subject;
    private String isbn;
    private long availableCopies;
}

