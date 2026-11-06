package com.smartlib;

import com.smartlib.config.AppProperties;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import com.smartlib.service.NotificationService;
import com.smartlib.service.WaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class WaitlistServiceTest {

    private WaitlistRepository waitlistRepository;
    private LoanRepository loanRepository;
    private UserRepository userRepository;
    private CopyRepository copyRepository;
    private BookRepository bookRepository;
    private NotificationService notificationService;
    private MongoTemplate mongoTemplate;
    private WaitlistService waitlistService;

    private Copy copy;

    @BeforeEach
    void setup() {
        waitlistRepository = mock(WaitlistRepository.class);
        loanRepository = mock(LoanRepository.class);
        userRepository = mock(UserRepository.class);
        copyRepository = mock(CopyRepository.class);
        bookRepository = mock(BookRepository.class);
        notificationService = mock(NotificationService.class);
        mongoTemplate = mock(MongoTemplate.class);
        waitlistService = new WaitlistService(waitlistRepository, loanRepository, userRepository,
                copyRepository, bookRepository, notificationService, mongoTemplate, new AppProperties());

        copy = Copy.builder().id("copy1").bookId("book1").status(CopyStatus.RESERVED).build();
    }

    private WaitlistEntry waitingEntry(String id, String loanId, Instant joinedAt) {
        return WaitlistEntry.builder().id(id).bookId("book1").studentId("s-" + id)
                .loanId(loanId).joinedAt(joinedAt).status(WaitlistStatus.WAITING).build();
    }

    @Test
    void releaseCopy_promotesEarliestWaiter_copyStaysReserved_andNotifies() {
        WaitlistEntry oldest = waitingEntry("entry1", "loan2", Instant.now().minusSeconds(600));
        when(waitlistRepository.findFirstByBookIdAndStatusOrderByJoinedAtAsc("book1", WaitlistStatus.WAITING))
                .thenReturn(Optional.of(oldest));
        Loan promotedLoan = Loan.builder().id("loan2").studentId("s-entry1").bookId("book1")
                .status(LoanStatus.WAITLISTED).requestDate(Instant.now()).build();
        when(loanRepository.findById("loan2")).thenReturn(Optional.of(promotedLoan));
        when(copyRepository.findById("copy1")).thenReturn(Optional.of(copy));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(WaitlistEntry.class)))
                .thenAnswer(inv -> {
                    oldest.setStatus(WaitlistStatus.FULFILLED);
                    oldest.setFulfilledAt(Instant.now());
                    return oldest;
                });
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Loan.class)))
                .thenAnswer(inv -> {
                    Update update = inv.getArgument(1);
                    org.bson.Document set = (org.bson.Document) update.getUpdateObject().get("$set");
                    promotedLoan.setStatus((LoanStatus) set.get("status"));
                    promotedLoan.setCopyId((String) set.get("copyId"));
                    promotedLoan.setPickupDeadline((Instant) set.get("pickupDeadline"));
                    return promotedLoan;
                });

        Loan promoted = waitlistService.releaseCopy("book1", "copy1");

        assertThat(promoted).isNotNull();
        assertThat(promoted.getStatus()).isEqualTo(LoanStatus.READY_FOR_PICKUP);
        assertThat(promoted.getCopyId()).isEqualTo("copy1");
        assertThat(promoted.getPickupDeadline()).isNotNull();
        assertThat(oldest.getStatus()).isEqualTo(WaitlistStatus.FULFILLED);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.RESERVED);
        verify(copyRepository, never()).save(any(Copy.class));
        // promotion notifies the student with the deadline
        verify(notificationService).create(eq("s-entry1"), eq(NotificationType.PROMOTED),
                anyString(), anyString(), eq("loan2"));
    }

    @Test
    void releaseCopy_emptyQueue_copyBecomesAvailable() {
        when(waitlistRepository.findFirstByBookIdAndStatusOrderByJoinedAtAsc("book1", WaitlistStatus.WAITING))
                .thenReturn(Optional.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(WaitlistEntry.class))).thenReturn(null);
        when(copyRepository.findById("copy1")).thenReturn(Optional.of(copy));
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> inv.getArgument(0));

        Loan promoted = waitlistService.releaseCopy("book1", "copy1");

        assertThat(promoted).isNull();
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    void queuePosition_countsOnlyOlderWaitingEntries() {
        WaitlistEntry entry = waitingEntry("entry3", "loan3", Instant.now().minusSeconds(60));
        when(waitlistRepository.countByBookIdAndStatusAndJoinedAtLessThan("book1", WaitlistStatus.WAITING, entry.getJoinedAt()))
                .thenReturn(2L);

        int position = waitlistService.queuePosition(entry);

        assertThat(position).isEqualTo(3);
    }

    @Test
    void cancelByLoanId_marksEntryRemoved() {
        WaitlistEntry entry = waitingEntry("entry1", "loan1", Instant.now());
        when(waitlistRepository.findByLoanId("loan1")).thenReturn(Optional.of(entry));
        when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        waitlistService.cancelByLoanId("loan1");

        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.REMOVED);
        verify(waitlistRepository).save(argThat(e -> e.getStatus() == WaitlistStatus.REMOVED));
    }
}
