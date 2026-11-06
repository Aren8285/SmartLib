package com.smartlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "copies")
@CompoundIndex(name = "copy_book_status_idx", def = "{'bookId': 1, 'status': 1}")
public class Copy {

    @Id
    private String id;

    private String bookId;

    @Builder.Default
    private CopyStatus status = CopyStatus.AVAILABLE;

    @Builder.Default
    private String condition = "GOOD";

    /** Optional per-copy shelf; defaults to the book's shelf at creation time. */
    private String shelfId;
}
