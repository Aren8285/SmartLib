package com.smartlib.service;

import com.smartlib.dto.ActionItem;
import com.smartlib.dto.LibrarianDashboardDto;
import com.smartlib.dto.StudentDashboardDto;
import com.smartlib.model.Book;
import com.smartlib.model.Fine;
import com.smartlib.model.FineStatus;
import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import com.smartlib.model.ReflectionStatus;
import com.smartlib.model.User;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.FineRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.NotificationRepository;
import com.smartlib.repository.ReflectionRepository;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thin aggregations over existing repositories: count queries and small
 * projections only, never load-all-then-compute.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final FineRepository fineRepository;
    private final ReflectionRepository reflectionRepository;
    private final NotificationRepository notificationRepository;
    private final LoanService loanService;
    private final BookService bookService;
    private final ReflectionService reflectionService;

    public StudentDashboardDto forStudent(String studentId) {
        StudentDashboardDto d = new StudentDashboardDto();
        User user = userRepository.findById(studentId).orElse(null);
        d.setPoints(user != null ? user.getPoints() : 0);

        long ready = loanRepository.countByStudentIdAndStatusIn(studentId, List.of(LoanStatus.READY_FOR_PICKUP));
        long issued = loanRepository.countByStudentIdAndStatusIn(studentId, List.of(LoanStatus.ISSUED));
        long overdue = loanRepository.countByStudentIdAndStatusIn(studentId, List.of(LoanStatus.OVERDUE));
        d.setActiveLoans(ready + issued + overdue);
        d.setWaitlisted(loanRepository.countByStudentIdAndStatusIn(studentId, List.of(LoanStatus.WAITLISTED)));
        d.setReflectionsPending(loanRepository.countByStudentIdAndStatusIn(studentId,
                List.of(LoanStatus.REFLECTION_PENDING)));

        List<Fine> pendingFines = fineRepository.findByStudentIdOrderByIssueDateDesc(studentId).stream()
                .filter(f -> f.getStatus() == FineStatus.PENDING)
                .toList();
        d.setPendingFinesCount(pendingFines.size());
        d.setPendingFinesAmount(pendingFines.stream().mapToDouble(Fine::getAmount).sum());

        d.setNextDueDate(loanRepository
                .findByStudentIdAndStatusIn(studentId, List.of(LoanStatus.ISSUED, LoanStatus.OVERDUE))
                .stream().map(Loan::getDueDate).filter(Objects::nonNull)
                .min(Instant::compareTo).orElse(null));

        d.setActionsNeeded(loanRepository
                .findByStudentIdOrderByRequestDateDesc(studentId).stream()
                .filter(loan -> loan.getStatus() == LoanStatus.READY_FOR_PICKUP
                        || loan.getStatus() == LoanStatus.REFLECTION_PENDING)
                .map(loan -> loan.getStatus() == LoanStatus.READY_FOR_PICKUP
                        ? ActionItem.of(loan.getId(), titleOf(loan.getBookId()), loan.getStatus(),
                                loan.getPickupDeadline(), null,
                                "Pick up at the desk before the deadline")
                        : ActionItem.of(loan.getId(), titleOf(loan.getBookId()), loan.getStatus(),
                                null, null, "Write a reflection (or skip it)"))
                .collect(Collectors.toList()));

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("readyForPickup", ready);
        counts.put("issued", issued);
        counts.put("overdue", overdue);
        counts.put("waitlisted", d.getWaitlisted());
        counts.put("reflectionsPending", d.getReflectionsPending());
        d.setCounts(counts);

        d.setUnreadNotifications(notificationRepository.countByUserIdAndReadFalse(studentId));
        return d;
    }

    public LibrarianDashboardDto forLibrarian() {
        LibrarianDashboardDto d = new LibrarianDashboardDto();
        d.setReadyForPickup(loanRepository.countByStatusIn(List.of(LoanStatus.READY_FOR_PICKUP)));
        d.setIssued(loanRepository.countByStatusIn(List.of(LoanStatus.ISSUED)));
        d.setOverdue(loanRepository.countByStatusIn(List.of(LoanStatus.OVERDUE)));
        d.setPendingReflections(reflectionRepository.countByStatus(ReflectionStatus.PENDING));
        d.setPendingFines(fineRepository.countByStatus(FineStatus.PENDING));
        d.setTotalBooks(bookService.countBooks());
        d.setTotalCopies(bookService.countCopies());
        d.setCopiesByStatus(bookService.copyStatusCounts());

        List<ActionItem> items = new java.util.ArrayList<>();
        loanRepository.findByStatus(LoanStatus.READY_FOR_PICKUP,
                        Sort.by(Sort.Direction.ASC, "pickupDeadline")).stream()
                .limit(5)
                .forEach(loan -> items.add(ActionItem.of(loan.getId(), titleOf(loan.getBookId()),
                        loan.getStatus(), loan.getPickupDeadline(), null, "Issue the reserved copy")));
        loanRepository.findByStatus(LoanStatus.OVERDUE, Sort.by(Sort.Direction.ASC, "dueDate")).stream()
                .limit(5)
                .forEach(loan -> items.add(ActionItem.of(loan.getId(), titleOf(loan.getBookId()),
                        loan.getStatus(), null, loan.getDueDate(), "Handle this overdue loan")));
        d.setOldestActionsNeeded(items);
        return d;
    }

    private String titleOf(String bookId) {
        return bookRepository.findById(bookId).map(Book::getTitle).orElse("Unknown");
    }
}
