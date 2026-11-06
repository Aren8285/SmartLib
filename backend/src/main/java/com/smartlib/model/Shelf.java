package com.smartlib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One physical shelf on the library floor. The floor is modelled as a grid:
 * {@code row} and {@code column} are 1-based grid coordinates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "shelves")
@CompoundIndex(name = "shelf_grid_idx", def = "{'row': 1, 'column': 1}", unique = true)
public class Shelf {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String section;

    private int row;
    private int column;
    private int capacity;

    private String label;
}
