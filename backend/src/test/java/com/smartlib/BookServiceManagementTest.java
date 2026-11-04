package com.smartlib;

import com.smartlib.dto.AddCopiesDto;
import com.smartlib.dto.BookRequestDto;
import com.smartlib.dto.CopyDto;
import com.smartlib.dto.UpdateCopyStatusDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import com.smartlib.service.BookService;
import com.smartlib.service.WaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookServiceManagementTest {

    private BookRepository bookRepository;
    private CopyRepository copyRepository;
    private WaitlistRepository waitlistRepository;
    private LoanRepository loanRepository;
    private UserRepository userRepository;
    private WaitlistService waitlistService;
    private BookService bookService;

    private Book book;

    @BeforeEach
    void setup() {
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(CopyRepository.class);
        waitlistRepository = mock(WaitlistRepository.class);
        loanRepository = mock(LoanRepository.class);
        userRepository = mock(UserRepository.class);
        waitlistService = mock(WaitlistService.class);

        bookService = new BookService(bookRepository, copyRepository, waitlistRepository,
                loanRepository, userRepository, waitlistService);

        book = Book.builder().id("book1").title("Clean Code").author("Robert C. Martin")
                .subject("Computer Science").isbn("9780132350884").build();
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(waitlistRepository.countByBookIdAndStatus(anyString(), eq(WaitlistStatus.WAITING))).thenReturn(0L);
    }

    private BookRequestDto requestDto(String isbn) {
        BookRequestDto dto = new BookRequestDto();
        dto.setTitle("Clean Code");
        dto.setAuthor("Robert C. Martin");
        dto.setSubject("Computer Science");
        dto.setIsbn(isbn);
        return dto;
    }

    // ── ISBN validation ──

    @Test
    void create_bookMalformedIsbn_rejected() {
        assertThatThrownBy(() -> bookService.create(requestDto("123")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ISBN");
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void create_bookValidIsbn10_accepted() {
        when(bookRepository.findByIsbn("0132350882")).thenReturn(null);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0);
            b.setId("new-book");
            return b;
        });
        when(copyRepository.countByBookIdAndStatus(anyString(), any(CopyStatus.class))).thenReturn(0L);

        // 0132350882 is a valid ISBN-10
        bookService.create(requestDto("0-13235088-2"));

        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void create_duplicateIsbn_rejected() {
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(
                Book.builder().id("other").title("Other Book").isbn("9780132350884").build());

        assertThatThrownBy(() -> bookService.create(requestDto("978-0132350884")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in use");
    }

    // ── deletion rules ──

    @Test
    void delete_withHeldCopies_rejected() {
        when(copyRepository.countByBookIdAndStatus("book1", CopyStatus.RESERVED)).thenReturn(1L);
        when(copyRepository.countByBookIdAndStatus("book1", CopyStatus.ISSUED)).thenReturn(0L);

        assertThatThrownBy(() -> bookService.delete("book1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reserved or issued");
        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void delete_withNonTerminalLoan_rejected() {
        when(copyRepository.countByBookIdAndStatus(anyString(), any(CopyStatus.class))).thenReturn(0L);
        when(loanRepository.existsByBookIdAndStatusIn(eq("book1"), anyList())).thenReturn(true);

        assertThatThrownBy(() -> bookService.delete("book1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("loans or waitlist");
        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void delete_finishedRentalsOnly_deletesBookAndCopies() {
        when(copyRepository.countByBookIdAndStatus(anyString(), any(CopyStatus.class))).thenReturn(0L);
        when(loanRepository.existsByBookIdAndStatusIn(eq("book1"), anyList())).thenReturn(false);
        when(copyRepository.findByBookId("book1")).thenReturn(List.of(
                Copy.builder().id("c1").bookId("book1").build()));

        bookService.delete("book1");

        verify(copyRepository).deleteAll(anyList());
        verify(bookRepository).delete(book);
    }

    // ── adding copies promotes the queue ──

    @Test
    void addCopies_eachNewCopyGoesThroughReleaseRoutine() {
        AddCopiesDto dto = new AddCopiesDto();
        dto.setCount(2);
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> {
            Copy c = inv.getArgument(0);
            if (c.getId() == null) c.setId("copy-" + c.hashCode());
            return c;
        });
        when(copyRepository.findById(anyString())).thenReturn(Optional.empty());
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());

        List<CopyDto> added = bookService.addCopies("book1", dto);

        assertThat(added).hasSize(2);
        assertThat(added).allMatch(c -> c.getStatus() == CopyStatus.AVAILABLE);
        // each copy ran through the FIFO promotion logic so waiting students get served
        verify(waitlistService, times(2)).releaseCopy(eq("book1"), anyString());
    }

    // ── manual copy status transitions ──

    @Test
    void updateCopyStatus_reservedOrIssued_rejected() {
        Copy held = Copy.builder().id("c2").bookId("book1").status(CopyStatus.ISSUED).build();
        when(copyRepository.findById("c2")).thenReturn(Optional.of(held));

        UpdateCopyStatusDto dto = new UpdateCopyStatusDto();
        dto.setStatus(CopyStatus.LOST);

        assertThatThrownBy(() -> bookService.updateCopyStatus("c2", dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("held by a student");
    }

    @Test
    void updateCopyStatus_damagedToAvailable_runsReleaseRoutine() {
        Copy damaged = Copy.builder().id("c3").bookId("book1").status(CopyStatus.DAMAGED).build();
        when(copyRepository.findById("c3")).thenReturn(Optional.of(damaged));
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCopyStatusDto dto = new UpdateCopyStatusDto();
        dto.setStatus(CopyStatus.AVAILABLE);

        CopyDto result = bookService.updateCopyStatus("c3", dto);

        assertThat(result.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        verify(waitlistService).releaseCopy("book1", "c3");
    }

    @Test
    void updateCopyStatus_lostToAvailable_notAllowed() {
        Copy lost = Copy.builder().id("c4").bookId("book1").status(CopyStatus.LOST).build();
        when(copyRepository.findById("c4")).thenReturn(Optional.of(lost));

        UpdateCopyStatusDto dto = new UpdateCopyStatusDto();
        dto.setStatus(CopyStatus.AVAILABLE);

        assertThatThrownBy(() -> bookService.updateCopyStatus("c4", dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Unsupported copy status change");
    }

    @Test
    void updateCopyStatus_availableToDamaged_allowed() {
        Copy ok = Copy.builder().id("c5").bookId("book1").status(CopyStatus.AVAILABLE).build();
        when(copyRepository.findById("c5")).thenReturn(Optional.of(ok));
        when(copyRepository.save(any(Copy.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCopyStatusDto dto = new UpdateCopyStatusDto();
        dto.setStatus(CopyStatus.DAMAGED);

        CopyDto result = bookService.updateCopyStatus("c5", dto);

        assertThat(result.getStatus()).isEqualTo(CopyStatus.DAMAGED);
        verify(waitlistService, never()).releaseCopy(anyString(), anyString());
    }
}
