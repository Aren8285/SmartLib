package com.smartlib.controller;

import com.smartlib.dto.WaitlistEntryDto;
import com.smartlib.service.UserService;
import com.smartlib.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/** Ordered FIFO queue for one book — a librarian view. */
@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final UserService userService;

    @GetMapping("/book/{bookId}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public List<WaitlistEntryDto> forBook(@PathVariable String bookId) {
        return waitlistService.queueFor(bookId).stream()
                .map(entry -> {
                    WaitlistEntryDto d = new WaitlistEntryDto();
                    d.setPosition(waitlistService.queuePosition(entry));
                    d.setEntryId(entry.getId());
                    d.setStudentId(entry.getStudentId());
                    d.setLoanId(entry.getLoanId());
                    d.setJoinedAt(entry.getJoinedAt());
                    d.setStatus(entry.getStatus());
                    d.setStudentName(userService.getUser(entry.getStudentId()).getName());
                    return d;
                })
                .collect(Collectors.toList());
    }
}
