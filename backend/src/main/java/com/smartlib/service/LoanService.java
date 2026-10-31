package com.smartlib.service;

import com.smartlib.dto.LibrarianActionDto;
import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
import com.smartlib.dto.StudentActionDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.exception.ResourceNotFoundException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;

    public LoanResponse requestLoan(LoanRequestDto dto) {
        // Validate student
        User student = userRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + dto.getStudentId()));
        if (student.getRole() != Role.STUDENT) {
            throw new BusinessRuleException("Only students can request loans.");
        }

        // Validate book
        Book book = bookRepository.findById(dto.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + dto.getBookId()));

        // Check for duplicate active loan
        loanRepository.findByStudentIdAndBookIdAndStatusIn(
                dto.getStudentId(), dto.getBookId(),
                List.of(LoanStatus.REQUESTED, LoanStatus.ISSUED))
                .ifPresent(l -> {
                    throw new BusinessRuleException(
                            "You already have an active loan for this book (status: " + l.getStatus() + ").");
                });

        // Check availability
        long available = copyRepository.countByBookIdAndStatus(dto.getBookId(), CopyStatus.AVAILABLE);
        if (available == 0) {
            throw new BusinessRuleException("Book is currently unavailable (waitlist coming in a later iteration).");
        }

        Loan loan = Loan.builder()
                .studentId(dto.getStudentId())
                .bookId(dto.getBookId())
                .status(LoanStatus.REQUESTED)
                .requestDate(Instant.now())
                .build();
        loan = loanRepository.save(loan);
        return toResponse(loan, book.getTitle(), student.getName());
    }

    public List<LoanResponse> getLoansByStudent(String studentId) {
        return loanRepository.findByStudentIdOrderByRequestDateDesc(studentId)
                .stream()
                .map(loan -> {
                    String bookTitle = bookRepository.findById(loan.getBookId())
                            .map(Book::getTitle).orElse("Unknown");
                    User student = userRepository.findById(loan.getStudentId()).orElse(null);
                    String studentName = student != null ? student.getName() : "Unknown";
                    return toResponse(loan, bookTitle, studentName);
                })
                .collect(Collectors.toList());
    }

    public List<LoanResponse> getLoansByStatus(LoanStatus status) {
        return loanRepository.findByStatus(status)
                .stream()
                .map(loan -> {
                    String bookTitle = bookRepository.findById(loan.getBookId())
                            .map(Book::getTitle).orElse("Unknown");
                    User student = userRepository.findById(loan.getStudentId()).orElse(null);
                    String studentName = student != null ? student.getName() : "Unknown";
                    return toResponse(loan, bookTitle, studentName);
                })
                .collect(Collectors.toList());
    }

    public LoanResponse issueLoan(String loanId, LibrarianActionDto dto) {
        // Validate librarian
        User librarian = userRepository.findById(dto.getLibrarianId())
                .orElseThrow(() -> new ResourceNotFoundException("Librarian not found: " + dto.getLibrarianId()));
        if (librarian.getRole() != Role.LIBRARIAN) {
            throw new BusinessRuleException("Only librarians can issue loans.");
        }

        // Validate loan
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new BusinessRuleException("Only REQUESTED loans can be issued. Current status: " + loan.getStatus());
        }

        // Find an available copy
        List<Copy> availableCopies = copyRepository.findByBookIdAndStatus(loan.getBookId(), CopyStatus.AVAILABLE);
        if (availableCopies.isEmpty()) {
            throw new BusinessRuleException("No available copies for this book.");
        }
        Copy copy = availableCopies.get(0);

        // Update copy
        copy.setStatus(CopyStatus.ISSUED);
        copyRepository.save(copy);

        // Update loan
        Instant now = Instant.now();
        loan.setCopyId(copy.getId());
        loan.setStatus(LoanStatus.ISSUED);
        loan.setIssueDate(now);
        loan.setDueDate(now.plus(14, ChronoUnit.DAYS));
        loan = loanRepository.save(loan);

        String bookTitle = bookRepository.findById(loan.getBookId()).map(Book::getTitle).orElse("Unknown");
        User student = userRepository.findById(loan.getStudentId()).orElse(null);
        String studentName = student != null ? student.getName() : "Unknown";
        return toResponse(loan, bookTitle, studentName);
    }

    public LoanResponse returnLoan(String loanId, LibrarianActionDto dto) {
        // Validate librarian
        User librarian = userRepository.findById(dto.getLibrarianId())
                .orElseThrow(() -> new ResourceNotFoundException("Librarian not found: " + dto.getLibrarianId()));
        if (librarian.getRole() != Role.LIBRARIAN) {
            throw new BusinessRuleException("Only librarians can mark returns.");
        }

        // Validate loan
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));
        if (loan.getStatus() != LoanStatus.ISSUED) {
            throw new BusinessRuleException("Only ISSUED loans can be returned. Current status: " + loan.getStatus());
        }

        // Update copy back to available
        String copyId = loan.getCopyId();
        Copy copy = copyRepository.findById(copyId)
                .orElseThrow(() -> new ResourceNotFoundException("Copy not found: " + copyId));
        copy.setStatus(CopyStatus.AVAILABLE);
        copyRepository.save(copy);

        // Update loan
        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(Instant.now());
        loan = loanRepository.save(loan);

        String bookTitle = bookRepository.findById(loan.getBookId()).map(Book::getTitle).orElse("Unknown");
        User student = userRepository.findById(loan.getStudentId()).orElse(null);
        String studentName = student != null ? student.getName() : "Unknown";
        return toResponse(loan, bookTitle, studentName);
    }

    public LoanResponse cancelLoan(String loanId, StudentActionDto dto) {
        // Validate student
        User student = userRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + dto.getStudentId()));

        // Validate loan
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));
        if (!loan.getStudentId().equals(dto.getStudentId())) {
            throw new BusinessRuleException("You can only cancel your own loans.");
        }
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new BusinessRuleException("Only REQUESTED loans can be cancelled. Current status: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.CANCELLED);
        loan = loanRepository.save(loan);

        String bookTitle = bookRepository.findById(loan.getBookId()).map(Book::getTitle).orElse("Unknown");
        return toResponse(loan, bookTitle, student.getName());
    }

    private LoanResponse toResponse(Loan loan, String bookTitle, String studentName) {
        LoanResponse r = new LoanResponse();
        r.setId(loan.getId());
        r.setStudentId(loan.getStudentId());
        r.setStudentName(studentName);
        r.setBookId(loan.getBookId());
        r.setBookTitle(bookTitle);
        r.setCopyId(loan.getCopyId());
        r.setStatus(loan.getStatus());
        r.setRequestDate(loan.getRequestDate());
        r.setIssueDate(loan.getIssueDate());
        r.setDueDate(loan.getDueDate());
        r.setReturnDate(loan.getReturnDate());
        return r;
    }
}
