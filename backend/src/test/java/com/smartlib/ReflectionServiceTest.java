package com.smartlib;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.ReflectionDto;
import com.smartlib.dto.SubmitReflectionDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.ReflectionRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.service.NotificationService;
import com.smartlib.service.ReflectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReflectionServiceTest {

    private ReflectionRepository reflectionRepository;
    private LoanRepository loanRepository;
    private BookRepository bookRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private MongoTemplate mongoTemplate;
    private ReflectionService reflectionService;

    private User student;
    private User librarian;
    private Book book;
    private Loan loan;

    @BeforeEach
    void setup() {
        reflectionRepository = mock(ReflectionRepository.class);
        loanRepository = mock(LoanRepository.class);
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        mongoTemplate = mock(MongoTemplate.class);

        reflectionService = new ReflectionService(reflectionRepository, loanRepository, bookRepository,
                userRepository, notificationService, mongoTemplate, new AppProperties());

        student = User.builder().id("student1").name("Aarav Student").role(Role.STUDENT).build();
        librarian = User.builder().id("lib1").name("Meera Librarian").role(Role.LIBRARIAN).build();
        book = Book.builder().id("book1").title("Clean Code").author("Robert C. Martin")
                .subject("Computer Science").build();
        loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.REFLECTION_PENDING).build();
    }

    private SubmitReflectionDto submitDto(String content) {
        SubmitReflectionDto dto = new SubmitReflectionDto();
        dto.setLoanId("loan1");
        dto.setContent(content);
        return dto;
    }

    private Reflection pendingReflection() {
        return Reflection.builder().id("r1").studentId("student1").loanId("loan1").bookId("book1")
                .content("A long enough reflection content for the minimum length rule.")
                .status(ReflectionStatus.PENDING).build();
    }

    @Test
    void submit_tooShortContent_rejected() {
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(reflectionRepository.existsByLoanId("loan1")).thenReturn(false);

        assertThatThrownBy(() -> reflectionService.submit("student1", submitDto("too short")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void submit_otherStudentsLoan_forbidden() {
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> reflectionService.submit("someoneElse", submitDto(
                "this is a long reflection that exceeds the minimum length requirement for sure")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void submit_valid_createsPendingAndNotifiesLibrarians() {
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(reflectionRepository.existsByLoanId("loan1")).thenReturn(false);
        when(reflectionRepository.save(any(Reflection.class))).thenAnswer(inv -> {
            Reflection r = inv.getArgument(0);
            r.setId("r1");
            return r;
        });
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        ReflectionDto dto = reflectionService.submit("student1", submitDto(
                "The book helped me see error-prone code differently; I now refactor first."));

        assertThat(dto.getStatus()).isEqualTo(ReflectionStatus.PENDING);
        verify(notificationService).createForLibrarians(eq(NotificationType.REFLECTION_PENDING),
                anyString(), anyString(), eq("loan1"));
    }

    @Test
    void approve_pending_awardsPointsExactlyOnceAndClosesLoan() {
        Reflection pending = pendingReflection();
        when(reflectionRepository.findById("r1")).thenReturn(Optional.of(pending));
        when(reflectionRepository.save(any(Reflection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        ReflectionDto dto = reflectionService.approve("r1", "lib1");

        assertThat(dto.getStatus()).isEqualTo(ReflectionStatus.APPROVED);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
        // atomic, single points increment
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(User.class));
        assertThat(dto.getReviewedBy()).isEqualTo("lib1");
        verify(notificationService).create(eq("student1"), eq(NotificationType.REFLECTION_APPROVED),
                anyString(), anyString(), eq("loan1"));
    }

    @Test
    void approve_alreadyReviewed_conflict_noExtraPoints() {
        Reflection approved = pendingReflection();
        approved.setStatus(ReflectionStatus.APPROVED);
        when(reflectionRepository.findById("r1")).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> reflectionService.approve("r1", "lib1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING");
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }

    @Test
    void reject_requiresNote() {
        when(reflectionRepository.findById("r1")).thenReturn(Optional.of(pendingReflection()));

        assertThatThrownBy(() -> reflectionService.reject("r1", "lib1", "   "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("review note is required");
    }

    @Test
    void reject_withNote_noPointsAndLoanCloses_withNotification() {
        Reflection pending = pendingReflection();
        when(reflectionRepository.findById("r1")).thenReturn(Optional.of(pending));
        when(reflectionRepository.save(any(Reflection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        ReflectionDto dto = reflectionService.reject("r1", "lib1", "Too vague; add concrete examples.");

        assertThat(dto.getStatus()).isEqualTo(ReflectionStatus.REJECTED);
        assertThat(dto.getReviewNote()).isEqualTo("Too vague; add concrete examples.");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
        verify(notificationService).create(eq("student1"), eq(NotificationType.REFLECTION_REJECTED),
                anyString(), contains("Too vague"), eq("loan1"));
    }
}
