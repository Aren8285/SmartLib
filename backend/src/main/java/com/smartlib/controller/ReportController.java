package com.smartlib.controller;

import com.smartlib.dto.ReportDtos.*;
import com.smartlib.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Librarian-only reports; the leaderboard has its own any-authenticated endpoint. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LIBRARIAN')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/overdue")
    public List<OverdueRow> overdue() {
        return reportService.overdue();
    }

    @GetMapping("/popular-books")
    public List<PopularBook> popularBooks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.popularBooks(from, to, limit);
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardRow> leaderboard(@RequestParam(defaultValue = "10") int limit) {
        return reportService.leaderboard(limit);
    }

    @GetMapping("/circulation")
    public CirculationReport circulation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.circulation(from, to);
    }

    @GetMapping("/fines")
    public FinesReport fines(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.fines(from, to);
    }

    @GetMapping("/waitlist")
    public WaitlistReport waitlist(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.waitlist(from, to);
    }

    @GetMapping("/inventory")
    public InventoryReport inventory() {
        return reportService.inventory();
    }

    /** CSV download; the browser must send the Bearer token, so the UI fetches a blob. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportService.CsvFile file = reportService.exportCsv(type, from, to);
        byte[] body = file.content().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
