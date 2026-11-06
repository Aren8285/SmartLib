package com.smartlib.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookRequestDto {

    @NotBlank(message = "Title is required.")
    @Size(max = 200, message = "Title must be at most 200 characters.")
    private String title;

    @NotBlank(message = "Author is required.")
    @Size(max = 120, message = "Author must be at most 120 characters.")
    private String author;

    @NotBlank(message = "Subject is required.")
    @Size(max = 60, message = "Subject must be at most 60 characters.")
    private String subject;

    /** Optional, but if given it must be a well-formed ISBN-10/13 and not already in use. */
    @Size(max = 20, message = "ISBN must be at most 20 characters.")
    private String isbn;

    /** Optional shelf assignment; validated by the service (existence + capacity). */
    @Size(max = 40, message = "shelfId is too long.")
    private String shelfId;
}
