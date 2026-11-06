package com.smartlib.controller;

import com.smartlib.dto.ReportDtos.LeaderboardRow;
import com.smartlib.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public-to-authenticated leaderboard: names and points only. Separate from
 * /api/reports so students can see their peer ranking too.
 */
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final ReportService reportService;

    @GetMapping
    public List<LeaderboardRow> leaderboard(@RequestParam(defaultValue = "10") int limit) {
        return reportService.leaderboard(limit);
    }
}
