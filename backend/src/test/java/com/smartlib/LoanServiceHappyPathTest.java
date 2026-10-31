package com.smartlib;

import com.smartlib.dto.LibrarianActionDto;
import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
import com.smartlib.dto.StudentActionDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LoanServiceHappyPathTest {

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private CopyRepository copyRepository;

    @InjectMocks
    private LoanService loanService;

    private User student;
    private User librarian;
    private Book book;
    private Copy copy;

    @BeforeEach
    void setup() {
        student = User.builder().id("student1").name("Aarav Student")
                .email("aarav@smartlib.test").role(Role.STUDENT).build();
        librarian = User.builder().id("lib1").name("Meera Librarian")
                .email("meera@smartlib.test").role(Role.LIBRARIAN).build();
        book = Book.builder().id("book1").title("Clean Code")
                .author("Robert C. Martin").subject("Computer Science").isbn("978-0132350884").build();
        copy = Copy.builder().id("copy1").bookId("book1").status(CopyStatus.AVAILABLE).build();
    }

    @Test
    void happyPath_request_issue_return() {
        // ---- STEP 1: Request ----
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(loanRepository.findByStudentIdAndBookIdAndStatusIn(eq("student1"), eq("book1"), anyList()))
                .thenReturn(Optional.empty());
        when(copyRepository.countByBookIdAndStatus("book1", CopyStatus.AVAILABLE)).thenReturn(1L);

        Loan savedRequestedLoan = Loan.builder()
                .id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.REQUESTED).requestDate(Instant.now()).build();
        when(loanRepository.save(any(Loan.class))).thenReturn(savedRequestedLoan);

        LoanRequestDto requestDto = new LoanRequestDto();
        requestDto.setStudentId("student1");
        requestDto.setBookId("book1");
        LoanResponse requestResponse = loanService.requestLoan(requestDto);

        assertThat(requestResponse.getStatus()).isEqualTo(LoanStatus.REQUESTED);
        assertThat(requestResponse.getCopyId()).isNull();

        // ---- STEP 2: Issue ----
        // Copy status should start as AVAILABLE
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);

        when(userRepository.findById("lib1")).thenReturn(Optional.of(librarian));
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(savedRequestedLoan));
        when(copyRepository.findByBookIdAndStatus("book1", CopyStatus.AVAILABLE)).thenReturn(List.of(copy));

        Loan savedIssuedLoan = Loan.builder()
                .id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.ISSUED)
                .requestDate(savedRequestedLoan.getRequestDate())
                .issueDate(Instant.now())
                .dueDate(Instant.now().plusSeconds(14 * 24 * 3600))
                .build();
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanRepository.save(any(Loan.class))).thenReturn(savedIssuedLoan);
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        LibrarianActionDto issueDto = new LibrarianActionDto();
        issueDto.setLibrarianId("lib1");
        LoanResponse issueResponse = loanService.issueLoan("loan1", issueDto);

        assertThat(issueResponse.getStatus()).isEqualTo(LoanStatus.ISSUED);
        assertThat(issueResponse.getCopyId()).isEqualTo("copy1");
        assertThat(issueResponse.getDueDate()).isNotNull();
        // Verify copy was set to ISSUED (1st save call)
        verify(copyRepository, times(1)).save(argThat(c -> c.getStatus() == CopyStatus.ISSUED));

        // Reset interactions before return step
        clearInvocations(copyRepository, loanRepository);

        // ---- STEP 3: Return ----
        // Simulate copy now ISSUED
        copy.setStatus(CopyStatus.ISSUED);

        when(userRepository.findById("lib1")).thenReturn(Optional.of(librarian));
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(savedIssuedLoan));
        when(copyRepository.findById("copy1")).thenReturn(Optional.of(copy));

        Loan savedReturnedLoan = Loan.builder()
                .id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.RETURNED)
                .requestDate(savedRequestedLoan.getRequestDate())
                .issueDate(savedIssuedLoan.getIssueDate())
                .dueDate(savedIssuedLoan.getDueDate())
                .returnDate(Instant.now())
                .build();
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanRepository.save(any(Loan.class))).thenReturn(savedReturnedLoan);

        LibrarianActionDto returnDto = new LibrarianActionDto();
        returnDto.setLibrarianId("lib1");
        LoanResponse returnResponse = loanService.returnLoan("loan1", returnDto);

        assertThat(returnResponse.getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(returnResponse.getReturnDate()).isNotNull();
        // Verify copy was set back to AVAILABLE (1st save call after reset)
        verify(copyRepository, times(1)).save(argThat(c -> c.getStatus() == CopyStatus.AVAILABLE));
    }

    @Test
    void requestLoan_duplicateActiveLoan_throwsConflict() {
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        Loan existingLoan = Loan.builder().id("existingLoan").studentId("student1")
                .bookId("book1").status(LoanStatus.REQUESTED).build();
        when(loanRepository.findByStudentIdAndBookIdAndStatusIn(eq("student1"), eq("book1"), anyList()))
                .thenReturn(Optional.of(existingLoan));

        LoanRequestDto requestDto = new LoanRequestDto();
        requestDto.setStudentId("student1");
        requestDto.setBookId("book1");

        assertThatThrownBy(() -> loanService.requestLoan(requestDto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("active loan");
    }

    @Test
    void requestLoan_noCopiesAvailable_throwsConflict() {
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(loanRepository.findByStudentIdAndBookIdAndStatusIn(eq("student1"), eq("book1"), anyList()))
                .thenReturn(Optional.empty());
        when(copyRepository.countByBookIdAndStatus("book1", CopyStatus.AVAILABLE)).thenReturn(0L);

        LoanRequestDto requestDto = new LoanRequestDto();
        requestDto.setStudentId("student1");
        requestDto.setBookId("book1");

        assertThatThrownBy(() -> loanService.requestLoan(requestDto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unavailable");
    }
}
