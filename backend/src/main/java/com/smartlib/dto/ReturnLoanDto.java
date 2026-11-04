package com.smartlib.dto;

import com.smartlib.model.BookCondition;
import lombok.Data;

/** Optional return body: the observed condition of the returned copy. */
@Data
public class ReturnLoanDto {
    private BookCondition bookCondition;
}
