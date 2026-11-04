package com.smartlib.dto;

import com.smartlib.model.LoanStatus;
import lombok.Data;

import java.time.Instant;

/** One item a student or librarian needs to act on (thin projection of a loan). */
@Data
public class ActionItem {
    private String loanId;
    private String bookTitle;
    private LoanStatus status;
    private Instant pickupDeadline;
    private Instant dueDate;
    /** For student view: what to do ("write reflection", "pick up", "return now"...). */
    private String action;

    public static ActionItem of(String loanId, String bookTitle, LoanStatus status,
                                Instant pickupDeadline, Instant dueDate, String action) {
        ActionItem item = new ActionItem();
        item.setLoanId(loanId);
        item.setBookTitle(bookTitle);
        item.setStatus(status);
        item.setPickupDeadline(pickupDeadline);
        item.setDueDate(dueDate);
        item.setAction(action);
        return item;
    }
}
