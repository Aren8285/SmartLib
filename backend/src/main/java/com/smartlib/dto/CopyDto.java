package com.smartlib.dto;

import com.smartlib.model.CopyStatus;
import lombok.Data;

/** Copy as seen by librarians, including the current holder when held/issued. */
@Data
public class CopyDto {
    private String id;
    private String bookId;
    private CopyStatus status;
    private String condition;
    private String holderStudentId;
    private String holderName;
}
