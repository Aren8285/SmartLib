package com.smartlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "books")
public class Book {

    @Id
    private String id;

    private String title;
    private String author;
    private String subject;

    /** Unique when present; sparse so books without an ISBN are still allowed. */
    @Indexed(unique = true, sparse = true)
    private String isbn;

    /** Physical shelf (e.g. "A3"); optional so books can exist before being shelved. */
    @Indexed
    private String shelfId;

    private Instant createdAt;
}
