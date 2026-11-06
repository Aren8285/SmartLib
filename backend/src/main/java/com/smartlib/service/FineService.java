package com.smartlib.service;

import com.smartlib.dto.FineDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.Fine;
import com.smartlib.model.FineReason;
import com.smartlib.model.FineStatus;
import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import com.smartlib.model.NotificationType;
import com.smartlib.model.User;
import com.smartlib.repository.FineRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FineService {

    private final FineRepository fineRepository;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;

    public Fine create(String studentId, String loanId, double amount, FineReason reason) {
        return fineRepository.save(Fine.builder()
                .studentId(studentId)
                .loanId(loanId)
                .amount(amount)
                .reason(reason)
                .issueDate(Instant.now())
                .status(FineStatus.PENDING)
                .build());
    }

    /** Combined damaged-return fine: damaged-book base plus the accrued late amount. */
    public Fine createDamaged(String studentId, String loanId, double damagedAmount, double lateAmount) {
        return fineRepository.save(Fine.builder()
                .studentId(studentId)
                .loanId(loanId)
                .amount(damagedAmount + lateAmount)
                .reason(FineReason.DAMAGED_BOOK)
                .lateAmount(lateAmount)
                .damagedAmount(damagedAmount)
                .issueDate(Instant.now())
                .status(FineStatus.PENDING)
                .build());
    }

    /**
     * Simulated payment by the fine's own owner. The status flip is a conditional
     * update, so a double-click pays once. Unblocks the loan:
     * LATE_RETURN / DAMAGED_BOOK -> REFLECTION_PENDING, LOST_BOOK -> CLOSED.
     */
    public FineDto pay(String fineId, String studentId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new ResourceNotFoundException("Fine not found: " + fineId));

        if (!fine.getStudentId().equals(studentId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only pay your own fines.");
        }
        if (fine.getStatus() != FineStatus.PENDING) {
            throw new BusinessRuleException("This fine has already been paid.");
        }

        Instant now = Instant.now();
        com.mongodb.client.result.UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(fineId).and("status").is(FineStatus.PENDING)),
                new Update().set("status", FineStatus.PAID).set("paidDate", now),
                Fine.class);
        if (result == null || result.getModifiedCount() == 0) {
            throw new BusinessRuleException("This fine was already paid or changed. Refresh and try again.");
        }
        fine.setStatus(FineStatus.PAID);
        fine.setPaidDate(now);

        if (fine.getLoanId() != null) {
            Loan loan = loanRepository.findById(fine.getLoanId()).orElse(null);
            if (loan != null && loan.getStatus() == LoanStatus.FINE_PENDING) {
                LoanStatus target = switch (fine.getReason()) {
                    case LATE_RETURN, DAMAGED_BOOK -> LoanStatus.REFLECTION_PENDING;
                    case LOST_BOOK -> LoanStatus.CLOSED;
                };
                com.mongodb.client.result.UpdateResult loanResult = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(loan.getId())
                                .and("status").is(LoanStatus.FINE_PENDING)),
                        new Update().set("status", target), Loan.class);
                if (loanResult != null && loanResult.getModifiedCount() > 0) {
                    loan.setStatus(target);
                }
            }
        }

        notificationService.create(studentId, NotificationType.FINE_PAID,
                "Fine paid",
                "Your fine of \u20B9" + (int) fine.getAmount() + " has been paid. Thank you!",
                fine.getLoanId());

        return toDto(fine);
    }

    public List<FineDto> listForStudent(String studentId) {
        return fineRepository.findByStudentIdOrderByIssueDateDesc(studentId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<FineDto> listByStatus(FineStatus status) {
        return fineRepository.findByStatusOrderByIssueDateDesc(status).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<FineDto> listAll() {
        return fineRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public FineDto toDto(Fine fine) {
        FineDto d = new FineDto();
        d.setId(fine.getId());
        d.setStudentId(fine.getStudentId());
        d.setLoanId(fine.getLoanId());
        d.setAmount(fine.getAmount());
        d.setReason(fine.getReason());
        d.setIssueDate(fine.getIssueDate());
        d.setPaidDate(fine.getPaidDate());
        d.setStatus(fine.getStatus());
        d.setLateAmount(fine.getLateAmount());
        d.setDamagedAmount(fine.getDamagedAmount());
        String studentName = userRepository.findById(fine.getStudentId())
                .map(User::getName).orElse("Unknown");
        d.setStudentName(studentName);
        return d;
    }
}
