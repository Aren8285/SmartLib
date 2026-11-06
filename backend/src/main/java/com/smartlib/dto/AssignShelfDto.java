package com.smartlib.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Assign a book to a shelf; a null/blank shelfId clears the assignment. */
@Data
public class AssignShelfDto {

    @Size(max = 40, message = "shelfId is too long.")
    private String shelfId;
}
