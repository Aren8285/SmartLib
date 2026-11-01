package com.smartlib.controller;

import com.smartlib.dto.FineDto;
import com.smartlib.dto.PayFineDto;
import com.smartlib.model.FineStatus;
import com.smartlib.service.FineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fines")
@RequiredArgsConstructor
public class FineController {

    private final FineService fineService;

    @GetMapping
    public List<FineDto> getFines(
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String status) {
        if (studentId != null) {
            return fineService.listForStudent(studentId);
        } else if (status != null) {
            return fineService.listByStatus(FineStatus.valueOf(status.toUpperCase()));
        }
        return fineService.listAll();
    }

    @PostMapping("/{id}/pay")
    public FineDto payFine(@PathVariable String id, @Valid @RequestBody PayFineDto dto) {
        return fineService.pay(id, dto);
    }
}
