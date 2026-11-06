package com.smartlib;

import com.smartlib.dto.FineDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.*;
import com.smartlib.repository.FineRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.service.FineService;
import com.smartlib.service.NotificationService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FineServiceTest {

    private FineRepository fineRepository;
    private LoanRepository loanRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private MongoTemplate mongoTemplate;
    private FineService fineService;

    private Fine lateFine;
    private Fine lostFine;

    @BeforeEach
    void setup() {
        fineRepository = mock(FineRepository.class);
        loanRepository = mock(LoanRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        mongoTemplate = mock(MongoTemplate.class);
        fineService = new FineService(fineRepository, loanRepository, userRepository,
                notificationService, mongoTemplate);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        lateFine = Fine.builder().id("fine1").studentId("student1").loanId("loan1")
                .amount(15).reason(FineReason.LATE_RETURN).status(FineStatus.PENDING).build();
        lostFine = Fine.builder().id("fine2").studentId("student1").loanId("loan1")
                .amount(500).reason(FineReason.LOST_BOOK).status(FineStatus.PENDING).build();
    }

    @Test
    void payLateFine_loanMovesToReflectionPending() {
        when(fineRepository.findById("fine1")).thenReturn(Optional.of(lateFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.FINE_PENDING).fineId("fine1").build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("student1")).thenReturn(Optional.of(
                User.builder().id("student1").name("Aarav Student").role(Role.STUDENT).build()));

        FineDto result = fineService.pay("fine1", "student1");

        assertThat(result.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(result.getPaidDate()).isNotNull();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.REFLECTION_PENDING);
        verify(notificationService).create(eq("student1"), eq(NotificationType.FINE_PAID),
                anyString(), anyString(), eq("loan1"));
    }

    @Test
    void payDamagedFine_loanMovesToReflectionPending() {
        Fine damagedFine = Fine.builder().id("fine3").studentId("student1").loanId("loan1")
                .amount(210).reason(FineReason.DAMAGED_BOOK)
                .lateAmount(10.0).damagedAmount(200.0).status(FineStatus.PENDING).build();
        when(fineRepository.findById("fine3")).thenReturn(Optional.of(damagedFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.FINE_PENDING).fineId("fine3").build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("student1")).thenReturn(Optional.of(
                User.builder().id("student1").name("Aarav Student").role(Role.STUDENT).build()));

        FineDto result = fineService.pay("fine3", "student1");

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.REFLECTION_PENDING);
        assertThat(result.getLateAmount()).isEqualTo(10.0);
        assertThat(result.getDamagedAmount()).isEqualTo(200.0);
    }

    @Test
    void payLostFine_loanCloses() {
        when(fineRepository.findById("fine2")).thenReturn(Optional.of(lostFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.FINE_PENDING).fineId("fine2").build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("student1")).thenReturn(Optional.of(
                User.builder().id("student1").name("Aarav Student").role(Role.STUDENT).build()));

        FineDto result = fineService.pay("fine2", "student1");

        assertThat(result.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
    }

    @Test
    void payOtherStudentsFine_throwsForbidden() {
        when(fineRepository.findById("fine1")).thenReturn(Optional.of(lateFine));

        assertThatThrownBy(() -> fineService.pay("fine1", "student2"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("You can only pay your own fines");
    }

    @Test
    void payAlreadyPaidFine_throwsConflict() {
        lateFine.setStatus(FineStatus.PAID);
        when(fineRepository.findById("fine1")).thenReturn(Optional.of(lateFine));

        assertThatThrownBy(() -> fineService.pay("fine1", "student1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been paid");
    }

    @Test
    void payFine_nonexistent_throwsNotFound() {
        when(fineRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fineService.pay("nope", "student1"))
                .hasMessageContaining("Fine not found");
    }
}
