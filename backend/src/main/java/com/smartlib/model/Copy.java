package com.smartlib.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "copies")
public class Copy {

    @Id
    private String id;

    private String bookId;

    @Builder.Default
    private CopyStatus status = CopyStatus.AVAILABLE;

    @Builder.Default
    private String condition = "GOOD";
}

