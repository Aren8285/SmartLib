package com.smartlib;

import com.smartlib.dto.FineDto;
import com.smartlib.dto.PayFineDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.*;
import com.smartlib.repository.FineRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.service.FineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FineServiceTest {

    private FineRepository fineRepository;
    private LoanRepository loanRepository;
    private UserRepository userRepository;
    private FineService fineService;

    private User student;
    private Fine lateFine;
    private Fine lostFine;

    @BeforeEach
    void setup() {
        fineRepository = mock(FineRepository.class);
        loanRepository = mock(LoanRepository.class);
        userRepository = mock(UserRepository.class);
        fineService = new FineService(fineRepository, loanRepository, userRepository);

        student = User.builder().id("student1").name("Aarav Student")
                .email("aarav@smartlib.test").role(Role.STUDENT).build();
        lateFine = Fine.builder().id("fine1").studentId("student1").loanId("loan1")
                .amount(15).reason(FineReason.LATE_RETURN).status(FineStatus.PENDING).build();
        lostFine = Fine.builder().id("fine2").studentId("student1").loanId("loan1")
                .amount(500).reason(FineReason.LOST_BOOK).status(FineStatus.PENDING).build();
    }

    private PayFineDto payDto() {
        PayFineDto dto = new PayFineDto();
        dto.setStudentId("student1");
        return dto;
    }

    @Test
    void payLateFine_loanMovesToReturned() {
        when(fineRepository.findById("fine1")).thenReturn(Optional.of(lateFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.FINE_PENDING).fineId("fine1").build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));

        FineDto result = fineService.pay("fine1", payDto());

        assertThat(result.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(result.getPaidDate()).isNotNull();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.RETURNED);
    }

    @Test
    void payLostFine_loanCloses() {
        when(fineRepository.findById("fine2")).thenReturn(Optional.of(lostFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.FINE_PENDING).fineId("fine2").build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));

        FineDto result = fineService.pay("fine2", payDto());

        assertThat(result.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
    }

    @Test
    void payOtherStudentsFine_throwsConflict() {
        when(fineRepository.findById("fine1")).thenReturn(Optional.of(lateFine));
        PayFineDto dto = new PayFineDto();
        dto.setStudentId("student2");

        assertThatThrownBy(() -> fineService.pay("fine1", dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("You can only pay your own fines");
    }

    @Test
    void payAlreadyPaidFine_throwsConflict() {
        lateFine.setStatus(FineStatus.PAID);
        when(fineRepository.findById("fine1")).thenReturn(Optional.of(lateFine));

        assertThatThrownBy(() -> fineService.pay("fine1", payDto()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been paid");
    }

    @Test
    void payFine_nonexistent_throwsNotFound() {
        when(fineRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fineService.pay("nope", payDto()))
                .hasMessageContaining("Fine not found");
    }
}
