package com.smartlib.dto;

import lombok.Data;

@Data
public class ShelfResponse {
    private String id;
    private String code;
    private String section;
    private int row;
    private int column;
    private int capacity;
    private String label;
    private long bookCount;
}
