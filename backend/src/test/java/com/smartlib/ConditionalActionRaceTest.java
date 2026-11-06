package com.smartlib;

import com.smartlib.config.AppProperties;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.Book;
import com.smartlib.model.Copy;
import com.smartlib.model.CopyStatus;
import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import com.smartlib.model.User;
import com.smartlib.repository.*;
import com.smartlib.service.FineService;
import com.smartlib.service.LoanService;
import com.smartlib.service.NotificationService;
import com.smartlib.service.WaitlistService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The losing side of a double-click: the conditional status update matches zero
 * documents, so the service must return 409 and perform no side effects.
 */
class ConditionalActionRaceTest {

    private LoanRepository loanRepository;
    private UserRepository userRepository;
    private BookRepository bookRepository;
    private CopyRepository copyRepository;
    private WaitlistRepository waitlistRepository;
    private FineRepository fineRepository;
    private ReflectionRepository reflectionRepository;
    private MongoTemplate mongoTemplate;
    private WaitlistService waitlistService;
    private FineService fineService;
    private NotificationService notificationService;
    private LoanService loanService;

    @BeforeEach
    void setup() {
        loanRepository = mock(LoanRepository.class);
        userRepository = mock(UserRepository.class);
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(CopyRepository.class);
        waitlistRepository = mock(WaitlistRepository.class);
        fineRepository = mock(FineRepository.class);
        reflectionRepository = mock(ReflectionRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        waitlistService = mock(WaitlistService.class);
        fineService = mock(FineService.class);
        notificationService = mock(NotificationService.class);
        loanService = new LoanService(loanRepository, userRepository, bookRepository, copyRepository,
                waitlistRepository, fineRepository, reflectionRepository, mongoTemplate,
                waitlistService, fineService, notificationService, new AppProperties());

        when(userRepository.findById("student1")).thenReturn(Optional.of(
                User.builder().id("student1").name("Aarav").role(com.smartlib.model.Role.STUDENT).build()));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(
                Book.builder().id("book1").title("Clean Code").author("Martin").subject("CS").build()));
    }

    private void loseRace() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Loan.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 0L, null));
    }

    @Test
    void secondIssueLosesThePreconditionAndDoesNotTouchTheCopy() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.READY_FOR_PICKUP)
                .pickupDeadline(Instant.now().plus(1, ChronoUnit.HOURS)).build();
        Copy copy = Copy.builder().id("copy1").bookId("book1").status(CopyStatus.RESERVED).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(copyRepository.findById("copy1")).thenReturn(Optional.of(copy));
        loseRace();

        assertThatThrownBy(() -> loanService.issueLoan("loan1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already issued");
        verify(copyRepository, never()).save(any(Copy.class));
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void secondReturnLosesThePreconditionWithoutPointsFineOrQueuePromotion() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.ISSUED)
                .issueDate(Instant.now().minus(2, ChronoUnit.DAYS))
                .dueDate(Instant.now().plus(5, ChronoUnit.DAYS)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        loseRace();

        assertThatThrownBy(() -> loanService.returnLoan("loan1", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been returned");
        verify(fineService, never()).create(any(), any(), anyDouble(), any());
        verify(waitlistService, never()).releaseCopy(any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }

    @Test
    void secondCancelLosesThePreconditionWithoutRemovingTheQueueEntry() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.WAITLISTED).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        loseRace();

        assertThatThrownBy(() -> loanService.cancelLoan("loan1", "student1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already cancelled");
        verify(waitlistService, never()).cancelByLoanId(any());
    }

    @Test
    void secondLostReportLosesThePreconditionWithoutCreatingASecondFine() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.ISSUED)
                .dueDate(Instant.now().plus(2, ChronoUnit.DAYS)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(copyRepository.findById("copy1")).thenReturn(Optional.of(
                Copy.builder().id("copy1").bookId("book1").status(CopyStatus.ISSUED).build()));
        loseRace();

        assertThatThrownBy(() -> loanService.lostLoan("loan1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been returned");
        verify(fineService, never()).create(any(), any(), anyDouble(), any());
        verify(copyRepository, never()).save(any(Copy.class));
    }
}
