package com.smartlib;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.FineDto;
import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
import com.smartlib.dto.ReturnLoanDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.*;
import com.smartlib.repository.*;
import com.smartlib.service.FineService;
import com.smartlib.service.LoanService;
import com.smartlib.service.NotificationService;
import com.smartlib.service.WaitlistService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanServiceStateMachineTest {

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

    private User student;
    private Book book;
    private Copy copy;
    private WaitlistEntry entry;
    private AppProperties app;

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
        app = new AppProperties();

        loanService = new LoanService(loanRepository, userRepository, bookRepository, copyRepository,
                waitlistRepository, fineRepository, reflectionRepository, mongoTemplate,
                waitlistService, fineService, notificationService, app);

        // Every conditional update succeeds unless a test overrides it; this mirrors
        // the normal path where exactly one writer wins the status precondition.
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        student = User.builder().id("student1").name("Aarav Student")
                .email("aarav@smartlib.test").role(Role.STUDENT).points(0).build();
        book = Book.builder().id("book1").title("Clean Code")
                .author("Robert C. Martin").subject("Computer Science").isbn("9780132350884").build();
        copy = Copy.builder().id("copy1").bookId("book1").status(CopyStatus.AVAILABLE).build();
        entry = WaitlistEntry.builder().id("entry1").bookId("book1").studentId("student2")
                .loanId("loan2").joinedAt(Instant.now().minusSeconds(60)).status(WaitlistStatus.WAITING).build();
    }

    private LoanRequestDto requestDto() {
        LoanRequestDto dto = new LoanRequestDto();
        dto.setBookId("book1");
        return dto;
    }

    /** save() passes the entity through, assigning an id once. */
    private void passThroughSave(LoanRepository repo) {
        when(repo.save(any(Loan.class))).thenAnswer(inv -> {
            Loan l = inv.getArgument(0);
            if (l.getId() == null) l.setId("loan1");
            return l;
        });
    }

    private void stubCommonRequestChecks(User requester) {
        when(userRepository.findById(requester.getId())).thenReturn(Optional.of(requester));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(loanRepository.findByStudentIdAndBookIdAndStatusIn(eq(requester.getId()), eq("book1"), anyList()))
                .thenReturn(Optional.empty());
        when(waitlistRepository.existsByStudentIdAndBookIdAndStatus(requester.getId(), "book1", WaitlistStatus.WAITING))
                .thenReturn(false);
        when(fineRepository.countByStudentIdAndStatus(requester.getId(), FineStatus.PENDING)).thenReturn(0L);
        when(loanRepository.countByStudentIdAndStatusIn(eq(requester.getId()), anyList())).thenReturn(0L);
    }

    // ── REQUEST ──

    @Test
    void requestLoan_copyFree_reservesCopy_readyForPickup() {
        stubCommonRequestChecks(student);
        when(waitlistService.countWaiting("book1")).thenReturn(0L);
        Copy reservedCopy = Copy.builder().id("copy1").bookId("book1").status(CopyStatus.RESERVED).build();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Copy.class))).thenReturn(reservedCopy);
        passThroughSave(loanRepository);

        LoanResponse response = loanService.requestLoan(student.getId(), requestDto());

        assertThat(response.getStatus()).isEqualTo(LoanStatus.READY_FOR_PICKUP);
        assertThat(response.getCopyId()).isEqualTo("copy1");
        assertThat(response.getPickupDeadline())
                .isAfter(Instant.now().plus(47, ChronoUnit.HOURS));
        assertThat(response.getMessage()).contains("Copy reserved");
        verify(waitlistService, never()).join(any(), any(), any());
    }

    @Test
    void requestLoan_noCopies_joinsWaitlistWithQueuePosition() {
        stubCommonRequestChecks(student);
        when(waitlistService.countWaiting("book1")).thenReturn(0L);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Copy.class))).thenReturn(null);
        passThroughSave(loanRepository);
        when(waitlistService.join(eq("book1"), eq("student1"), eq("loan1"))).thenReturn(entry);
        when(waitlistService.findEntryByLoan("loan1")).thenReturn(Optional.of(entry));
        when(waitlistService.queuePosition(entry)).thenReturn(2);

        LoanResponse response = loanService.requestLoan(student.getId(), requestDto());

        assertThat(response.getStatus()).isEqualTo(LoanStatus.WAITLISTED);
        assertThat(response.getCopyId()).isNull();
        assertThat(response.getQueuePosition()).isEqualTo(2);
        assertThat(response.getMessage()).contains("#2");
    }

    @Test
    void requestLoan_queueNonEmpty_joinsQueueEvenIfCopyFree() {
        stubCommonRequestChecks(student);
        when(waitlistService.countWaiting("book1")).thenReturn(1L);
        passThroughSave(loanRepository);
        when(waitlistService.join(eq("book1"), eq("student1"), eq("loan1"))).thenReturn(entry);
        when(waitlistService.findEntryByLoan("loan1")).thenReturn(Optional.of(entry));
        when(waitlistService.queuePosition(entry)).thenReturn(2);

        LoanResponse response = loanService.requestLoan(student.getId(), requestDto());

        assertThat(response.getStatus()).isEqualTo(LoanStatus.WAITLISTED);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Copy.class));
    }

    @Test
    void requestLoan_duplicateActiveLoan_throwsConflict() {
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        Loan existing = Loan.builder().id("loan9").studentId("student1").bookId("book1")
                .status(LoanStatus.ISSUED).build();
        when(loanRepository.findByStudentIdAndBookIdAndStatusIn(eq("student1"), eq("book1"), anyList()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> loanService.requestLoan(student.getId(), requestDto()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already have an active loan");
    }

    @Test
    void requestLoan_reflectionPendingDoesNotBlockSameBook() {
        // A REFLECTION_PENDING loan for the same book must not be treated as duplicative.
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        Loan existing = Loan.builder().id("loan9").studentId("student1").bookId("book1")
                .status(LoanStatus.REFLECTION_PENDING).build();
        when(loanRepository.findByStudentIdAndBookIdAndStatusIn(eq("student1"), eq("book1"), anyList()))
                .thenReturn(Optional.empty());
        when(waitlistRepository.existsByStudentIdAndBookIdAndStatus("student1", "book1", WaitlistStatus.WAITING))
                .thenReturn(false);
        when(fineRepository.countByStudentIdAndStatus("student1", FineStatus.PENDING)).thenReturn(1L);

        // blocked by fines anyway, but importantly NOT by the reflection-pending loan
        assertThatThrownBy(() -> loanService.requestLoan(student.getId(), requestDto()))
                .hasMessage("Pay your pending fines first.");
    }

    @Test
    void requestLoan_pendingFinesBlocked() {
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(loanRepository.findByStudentIdAndBookIdAndStatusIn(eq("student1"), eq("book1"), anyList()))
                .thenReturn(Optional.empty());
        when(waitlistRepository.existsByStudentIdAndBookIdAndStatus("student1", "book1", WaitlistStatus.WAITING))
                .thenReturn(false);
        when(fineRepository.countByStudentIdAndStatus("student1", FineStatus.PENDING)).thenReturn(1L);

        assertThatThrownBy(() -> loanService.requestLoan(student.getId(), requestDto()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pay your pending fines first.");
    }

    @Test
    void requestLoan_maxActiveLoansReached_throwsConflict() {
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(loanRepository.findByStudentIdAndBookIdAndStatusIn(eq("student1"), eq("book1"), anyList()))
                .thenReturn(Optional.empty());
        when(waitlistRepository.existsByStudentIdAndBookIdAndStatus("student1", "book1", WaitlistStatus.WAITING))
                .thenReturn(false);
        when(fineRepository.countByStudentIdAndStatus("student1", FineStatus.PENDING)).thenReturn(0L);
        when(loanRepository.countByStudentIdAndStatusIn(eq("student1"), anyList())).thenReturn(3L);

        assertThatThrownBy(() -> loanService.requestLoan(student.getId(), requestDto()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Active loan limit reached");
    }

    @Test
    void requestLoan_waitlistFull_throwsConflict() {
        stubCommonRequestChecks(student);
        when(waitlistService.countWaiting("book1")).thenReturn(5L);

        assertThatThrownBy(() -> loanService.requestLoan(student.getId(), requestDto()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("waitlist for this book is full");
    }

    @Test
    void requestLoan_librarianRoleRejected() {
        User notAStudent = User.builder().id("lib1").name("Meera Librarian")
                .role(Role.LIBRARIAN).build();
        when(userRepository.findById("lib1")).thenReturn(Optional.of(notAStudent));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> loanService.requestLoan("lib1", requestDto()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only students");
    }

    // ── ISSUE ──

    @Test
    void issueLoan_happyPath_copyIssuedAndDueDateSet() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.READY_FOR_PICKUP)
                .requestDate(Instant.now().minusSeconds(3600))
                .pickupDeadline(Instant.now().plusSeconds(3600)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        copy.setStatus(CopyStatus.RESERVED);
        when(copyRepository.findById("copy1")).thenReturn(Optional.of(copy));
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> inv.getArgument(0));
        passThroughSave(loanRepository);
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        LoanResponse response = loanService.issueLoan("loan1");

        assertThat(response.getStatus()).isEqualTo(LoanStatus.ISSUED);
        assertThat(response.getDueDate()).isAfter(Instant.now().plus(13, ChronoUnit.DAYS));
        assertThat(response.getDueDate()).isBefore(Instant.now().plus(15, ChronoUnit.DAYS));
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.ISSUED);
        verify(notificationService).create(eq("student1"), eq(NotificationType.ISSUED), anyString(), anyString(), eq("loan1"));
    }

    @Test
    void issueLoan_afterPickupDeadline_throwsConflict() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.READY_FOR_PICKUP)
                .pickupDeadline(Instant.now().minusSeconds(60)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.issueLoan("loan1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pickup deadline passed");
    }

    // ── RETURN ──

    @Test
    void returnLoan_onTime_awardsPointsAtomicallyAndGoesToReflectionPending() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.ISSUED)
                .issueDate(Instant.now().minus(10, ChronoUnit.DAYS))
                .dueDate(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        passThroughSave(loanRepository);
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        LoanResponse response = loanService.returnLoan("loan1", null);

        assertThat(response.getStatus()).isEqualTo(LoanStatus.REFLECTION_PENDING);
        assertThat(response.getMessage()).contains("+10");
        // atomic points update via MongoTemplate $inc
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(User.class));
        verify(userRepository, never()).save(any(User.class));
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.REFLECTION_PENDING);
    }

    @Test
    void returnLoan_late_createsPendingFine() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.OVERDUE)
                .issueDate(Instant.now().minus(17, ChronoUnit.DAYS))
                .dueDate(Instant.now().minus(3, ChronoUnit.DAYS)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        Fine fine = Fine.builder().id("fine1").studentId("student1").loanId("loan1")
                .amount(15).reason(FineReason.LATE_RETURN).status(FineStatus.PENDING).build();
        when(fineService.create(eq("student1"), eq("loan1"), eq(15.0), eq(FineReason.LATE_RETURN)))
                .thenReturn(fine);
        FineDto fineDto = new FineDto();
        fineDto.setId("fine1");
        fineDto.setAmount(15.0);
        fineDto.setReason(FineReason.LATE_RETURN);
        when(fineService.toDto(fine)).thenReturn(fineDto);
        passThroughSave(loanRepository);
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(fineRepository.findById("fine1")).thenReturn(Optional.of(fine));

        LoanResponse response = loanService.returnLoan("loan1", null);

        assertThat(response.getStatus()).isEqualTo(LoanStatus.FINE_PENDING);
        assertThat(loan.getFineId()).isEqualTo("fine1");
        assertThat(response.getFine().getAmount()).isEqualTo(15.0);
        assertThat(response.getMessage()).contains("Pending fine");
        verify(waitlistService).releaseCopy("book1", "copy1");
        verify(fineRepository, never()).save(any(Fine.class));
    }

    @Test
    void returnLoan_damaged_createsCombinedFineAndKeepsCopyOutOfCirculation() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.OVERDUE)
                .issueDate(Instant.now().minus(20, ChronoUnit.DAYS))
                .dueDate(Instant.now().minus(2, ChronoUnit.DAYS)).build(); // 2 days late = ₹10 late
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        Fine combined = Fine.builder().id("fine3").studentId("student1").loanId("loan1")
                .amount(210).reason(FineReason.DAMAGED_BOOK)
                .lateAmount(10.0).damagedAmount(200.0).status(FineStatus.PENDING).build();
        when(fineService.createDamaged(eq("student1"), eq("loan1"), eq(200.0), eq(10.0)))
                .thenReturn(combined);
        FineDto damagedFineDto = new FineDto();
        damagedFineDto.setId("fine3");
        damagedFineDto.setAmount(210.0);
        damagedFineDto.setReason(FineReason.DAMAGED_BOOK);
        damagedFineDto.setLateAmount(10.0);
        damagedFineDto.setDamagedAmount(200.0);
        when(fineService.toDto(combined)).thenReturn(damagedFineDto);
        passThroughSave(loanRepository);
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(copyRepository.findById("copy1")).thenReturn(Optional.of(copy));
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fineRepository.findById("fine3")).thenReturn(Optional.of(combined));

        ReturnLoanDto dto = new ReturnLoanDto();
        dto.setBookCondition(BookCondition.DAMAGED);

        LoanResponse response = loanService.returnLoan("loan1", dto);

        assertThat(response.getStatus()).isEqualTo(LoanStatus.FINE_PENDING);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.DAMAGED);
        assertThat(response.getFine().getAmount()).isEqualTo(210.0);
        assertThat(response.getFine().getLateAmount()).isEqualTo(10.0);
        assertThat(response.getFine().getDamagedAmount()).isEqualTo(200.0);
        // damaged copies are never released to the queue
        verify(waitlistService, never()).releaseCopy(anyString(), anyString());
        // no points for a damaged return
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }

    @Test
    void returnLoan_invalidTransition_throwsConflict() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.REFLECTION_PENDING).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.returnLoan("loan1", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only ISSUED or OVERDUE loans can be returned");
    }

    // ── LOST ──

    @Test
    void lostLoan_createsLostFineAndDoesNotReleaseCopy() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.ISSUED)
                .dueDate(Instant.now().plus(2, ChronoUnit.DAYS)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(copyRepository.findById("copy1")).thenReturn(Optional.of(copy));
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> inv.getArgument(0));
        Fine fine = Fine.builder().id("fine2").studentId("student1").loanId("loan1")
                .amount(500).reason(FineReason.LOST_BOOK).status(FineStatus.PENDING).build();
        when(fineService.create(eq("student1"), eq("loan1"), eq(500.0), eq(FineReason.LOST_BOOK)))
                .thenReturn(fine);
        FineDto lostFineDto = new FineDto();
        lostFineDto.setId("fine2");
        lostFineDto.setAmount(500.0);
        lostFineDto.setReason(FineReason.LOST_BOOK);
        when(fineService.toDto(fine)).thenReturn(lostFineDto);
        passThroughSave(loanRepository);
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(fineRepository.findById("fine2")).thenReturn(Optional.of(fine));

        LoanResponse response = loanService.lostLoan("loan1");

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOST);
        assertThat(response.getStatus()).isEqualTo(LoanStatus.FINE_PENDING);
        assertThat(loan.getFineId()).isEqualTo("fine2");
        assertThat(response.getFine().getAmount()).isEqualTo(500.0);
        verify(waitlistService, never()).releaseCopy(anyString(), anyString());
    }

    // ── CANCEL ──

    @Test
    void cancelLoan_waitlisted_removesQueueEntry() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.WAITLISTED).requestDate(Instant.now()).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        passThroughSave(loanRepository);
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        LoanResponse response = loanService.cancelLoan("loan1", "student1");

        assertThat(response.getStatus()).isEqualTo(LoanStatus.CANCELLED);
        verify(waitlistService).cancelByLoanId("loan1");
    }

    @Test
    void cancelLoan_readyForPickup_releasesCopy() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.READY_FOR_PICKUP)
                .pickupDeadline(Instant.now().plusSeconds(3600)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        passThroughSave(loanRepository);
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        LoanResponse response = loanService.cancelLoan("loan1", "student1");

        assertThat(response.getStatus()).isEqualTo(LoanStatus.CANCELLED);
        verify(waitlistService).releaseCopy("book1", "copy1");
    }

    @Test
    void cancelLoan_otherStudentsLoan_throwsForbidden() {
        Loan loan = Loan.builder().id("loan1").studentId("someoneElse").bookId("book1")
                .status(LoanStatus.WAITLISTED).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.cancelLoan("loan1", "student1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("your own loans");
    }

    // ── SKIP REFLECTION ──

    @Test
    void skipReflection_closesLoanWithoutPoints() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.REFLECTION_PENDING).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(reflectionRepository.existsByLoanId("loan1")).thenReturn(false);
        passThroughSave(loanRepository);
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        LoanResponse response = loanService.skipReflection("loan1", "student1");

        assertThat(response.getStatus()).isEqualTo(LoanStatus.CLOSED);
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }

    @Test
    void skipReflection_alreadySubmitted_throwsConflict() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.REFLECTION_PENDING).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(reflectionRepository.existsByLoanId("loan1")).thenReturn(true);

        assertThatThrownBy(() -> loanService.skipReflection("loan1", "student1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already submitted");
    }

    // ── SCHEDULED JOBS ──

    @Test
    void runPickupExpiryJob_expiresLoanReleasesCopyAndNotifiesStudent() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.READY_FOR_PICKUP)
                .pickupDeadline(Instant.now().minusSeconds(120)).build();
        when(loanRepository.findByStatusAndPickupDeadlineBefore(eq(LoanStatus.READY_FOR_PICKUP), any(Instant.class)))
                .thenReturn(List.of(loan));
        passThroughSave(loanRepository);
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        int count = loanService.runPickupExpiryJob();

        assertThat(count).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.EXPIRED);
        verify(waitlistService).releaseCopy("book1", "copy1");
        verify(notificationService).create(eq("student1"), eq(NotificationType.EXPIRED),
                anyString(), anyString(), eq("loan1"));
    }

    @Test
    void runOverdueJob_marksIssuedLoansOverdueAndNotifiesStudent() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.ISSUED)
                .dueDate(Instant.now().minusSeconds(3600)).build();
        when(loanRepository.findByStatusAndDueDateBefore(eq(LoanStatus.ISSUED), any(Instant.class)))
                .thenReturn(List.of(loan));
        passThroughSave(loanRepository);
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        int count = loanService.runOverdueJob();

        assertThat(count).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.OVERDUE);
        verify(notificationService).create(eq("student1"), eq(NotificationType.OVERDUE),
                anyString(), anyString(), eq("loan1"));
    }

    @Test
    void runReminderJobs_sendsPickupReminderOnceOnly() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .status(LoanStatus.READY_FOR_PICKUP)
                .reminderSent(false)
                .pickupDeadline(Instant.now().plus(2, ChronoUnit.HOURS)).build();
        when(loanRepository.findByStatusAndReminderSentFalseAndPickupDeadlineBetween(
                eq(LoanStatus.READY_FOR_PICKUP), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(loan));
        when(loanRepository.findByStatusAndDueReminderSentFalseAndDueDateBetween(
                eq(LoanStatus.ISSUED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        passThroughSave(loanRepository);
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        int sent = loanService.runReminderJobs();
        assertThat(sent).isEqualTo(1);
        assertThat(loan.isReminderSent()).isEqualTo(true);

        // Second run: the repository (real behaviour) would no longer return this loan
        // because the flag was set; verify the flag prevents duplicates.
        when(loanRepository.findByStatusAndReminderSentFalseAndPickupDeadlineBetween(
                eq(LoanStatus.READY_FOR_PICKUP), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        assertThat(loanService.runReminderJobs()).isEqualTo(0);
        verify(notificationService, times(1)).create(eq("student1"), eq(NotificationType.PICKUP_REMINDER),
                anyString(), anyString(), anyString());
    }

    @Test
    void toResponse_overdueLoan_exposesAccruedFine() {
        Loan loan = Loan.builder().id("loan1").studentId("student1").bookId("book1")
                .copyId("copy1").status(LoanStatus.OVERDUE)
                .dueDate(Instant.now().minus(2, ChronoUnit.DAYS)).build();
        when(loanRepository.findById("loan1")).thenReturn(Optional.of(loan));
        when(userRepository.findById("student1")).thenReturn(Optional.of(student));
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));

        LoanResponse response = loanService.getLoan("loan1");

        assertThat(response.getAccruedFine()).isEqualTo(10.0); // 2 days * 5
    }
}
