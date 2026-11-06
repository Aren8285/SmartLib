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
    private long totalCopies;
    private long waitlistSize;
    /** Shelf the book is assigned to, if any. */
    private String shelfId;
    /** Human-readable section of that shelf (e.g. "Computer Science"). */
    private String shelfSection;
    /** Short code shown in the UI and used by /map?shelf=CODE. */
    private String shelfCode;
}
