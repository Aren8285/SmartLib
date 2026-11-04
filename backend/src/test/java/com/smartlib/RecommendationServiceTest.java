package com.smartlib;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.BookResponse;
import com.smartlib.dto.RecommendationDto;
import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.ReflectionRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import com.smartlib.service.BookService;
import com.smartlib.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecommendationServiceTest {

    private LoanRepository loanRepository;
    private BookRepository bookRepository;
    private CopyRepository copyRepository;
    private WaitlistRepository waitlistRepository;
    private ReflectionRepository reflectionRepository;
    private UserRepository userRepository;
    private RecommendationService recommendationService;

    private User aarav;
    private Book aaravRead;      // "Clean Code" (CS)
    private Book sameSubjectNew; // "The Pragmatic Programmer" (CS) - unread by Aarav
    private Book peerRead;       // "1984" (Literature) - read by peer, not Aarav
    private Book unrelated;      // "Freakonomics" (Economics)

    @BeforeEach
    void setup() {
        loanRepository = mock(LoanRepository.class);
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(CopyRepository.class);
        waitlistRepository = mock(WaitlistRepository.class);
        reflectionRepository = mock(ReflectionRepository.class);
        userRepository = mock(UserRepository.class);

        BookService bookService = mock(BookService.class);
        when(bookService.toResponse(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0);
            BookResponse resp = new BookResponse();
            resp.setId(b.getId());
            resp.setTitle(b.getTitle());
            resp.setAuthor(b.getAuthor());
            resp.setSubject(b.getSubject());
            resp.setIsbn(b.getIsbn());
            resp.setAvailableCopies(1); // mirrors the countByBookIdAndStatus stub
            return resp;
        });

        AppProperties appProps = new AppProperties();
        appProps.setRecommendationLimit(8);
        recommendationService = new RecommendationService(loanRepository, bookRepository, copyRepository,
                waitlistRepository, reflectionRepository, userRepository, bookService, appProps);

        aarav = User.builder().id("aarav").name("Aarav").role(Role.STUDENT)
                .preferredSubjects(null).build();

        aaravRead = Book.builder().id("book-cc").title("Clean Code")
                .author("Robert C. Martin").subject("Computer Science").build();
        sameSubjectNew = Book.builder().id("book-pp").title("The Pragmatic Programmer")
                .author("Thomas & Hunt").subject("Computer Science").build();
        peerRead = Book.builder().id("book-1984").title("1984")
                .author("George Orwell").subject("Literature").build();
        unrelated = Book.builder().id("book-frk").title("Freakonomics")
                .author("Levitt & Dubner").subject("Economics").build();

        when(bookRepository.findAll()).thenReturn(List.of(aaravRead, sameSubjectNew, peerRead, unrelated));
        when(bookRepository.findById(anyString())).thenReturn(Optional.empty());
        when(copyRepository.countByBookIdAndStatus(anyString(), eq(CopyStatus.AVAILABLE))).thenReturn(1L);
        when(waitlistRepository.countByBookIdAndStatus(anyString(), eq(WaitlistStatus.WAITING))).thenReturn(0L);
        when(waitlistRepository.findByStudentIdAndStatus(anyString(), any(WaitlistStatus.class))).thenReturn(List.of());
    }

    private Loan pastLoan(String studentId, String bookId) {
        return Loan.builder().id("l-" + studentId + "-" + bookId)
                .studentId(studentId).bookId(bookId).status(LoanStatus.CLOSED)
                .issueDate(java.time.Instant.now()).dueDate(java.time.Instant.now())
                .requestDate(java.time.Instant.now())
                .returnDate(java.time.Instant.now()).build();
    }

    private void stubBookLookups() {
        when(bookRepository.findById("book-cc")).thenReturn(Optional.of(aaravRead));
        when(bookRepository.findById("book-pp")).thenReturn(Optional.of(sameSubjectNew));
        when(bookRepository.findById("book-1984")).thenReturn(Optional.of(peerRead));
        when(bookRepository.findById("book-frk")).thenReturn(Optional.of(unrelated));
    }

    // ── cold start ──

    @Test
    void coldStart_preferredSubjectsThenPopular() {
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), anyList())).thenReturn(List.of());
        User withPrefs = User.builder().id("aarav").name("Aarav").role(Role.STUDENT)
                .preferredSubjects(List.of("Physics")).build();
        when(userRepository.findById("aarav")).thenReturn(Optional.of(withPrefs));

        List<RecommendationDto> result = recommendationService.recommendFor("aarav");

        // no preferred-subject books exist in the stubbed catalogue -> falls back to popularity
        assertThat(result).isNotEmpty();
        assertThat(result).anySatisfy(dto ->
                assertThat(dto.getReason()).isEqualTo("Popular in the library"));
    }

    @Test
    void coldStart_preferredSubjectBooks_comeFirstWithPreferenceReason() {
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), anyList())).thenReturn(List.of());
        Book physicsBook = Book.builder().id("book-phy").title("University Physics")
                .author("Young").subject("Physics").build();
        when(bookRepository.findAll()).thenReturn(List.of(unrelated, physicsBook));
        when(bookRepository.findById("book-phy")).thenReturn(Optional.of(physicsBook));
        User withPrefs = User.builder().id("aarav").name("Aarav").role(Role.STUDENT)
                .preferredSubjects(List.of("Physics")).build();
        when(userRepository.findById("aarav")).thenReturn(Optional.of(withPrefs));

        List<RecommendationDto> result = recommendationService.recommendFor("aarav");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getBook().getTitle()).isEqualTo("University Physics");
        assertThat(result.get(0).getReason()).isEqualTo("Because you prefer Physics");
    }

    // ── subject affinity ──

    @Test
    void subjectAffinity_ranksSameSubjectUnreadBookFirst() {
        stubBookLookups();
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), eq(RecommendationService.READ_HISTORY_STATUSES)))
                .thenReturn(List.of(pastLoan("aarav", "book-cc")));
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), eq(RecommendationService.ACTIVE_EXCLUSION_STATUSES)))
                .thenReturn(List.of());
        when(loanRepository.findByStudentIdAndStatusIn(eq("basi"), anyList())).thenReturn(List.of());
        when(loanRepository.findByBookIdAndStatusIn(eq("book-cc"), anyList()))
                .thenReturn(List.of(pastLoan("aarav", "book-cc")));
        when(reflectionRepository.findByLoanId(anyString())).thenReturn(Optional.of(
                Reflection.builder().id("r1").loanId("l-aarav-book-cc").bookId("book-cc")
                        .status(ReflectionStatus.APPROVED).build()));
        when(userRepository.findById("aarav")).thenReturn(Optional.of(aarav));

        List<RecommendationDto> result = recommendationService.recommendFor("aarav");

        assertThat(result).isNotEmpty();
        // top answer: the unread CS book ("because you enjoy Computer Science")
        RecommendationDto top = result.stream()
                .filter(r -> r.getBook().getId().equals("book-pp"))
                .findFirst().orElseThrow();
        assertThat(top.getReason()).isEqualTo("Because you enjoy Computer Science");
        assertThat(top.getBook().getAvailableCopies()).isEqualTo(1);
        // unrelated books with no signals must not appear
        assertThat(result).noneSatisfy(r -> assertThat(r.getBook().getId()).isEqualTo("book-frk"));
    }

    // ── similar readers ──

    @Test
    void similarReaderSignal_movesPeerBooksIntoRecommendations() {
        stubBookLookups();
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), eq(RecommendationService.READ_HISTORY_STATUSES)))
                .thenReturn(List.of(pastLoan("aarav", "book-cc")));
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), eq(RecommendationService.ACTIVE_EXCLUSION_STATUSES)))
                .thenReturn(List.of());
        when(loanRepository.findByStudentIdAndStatusIn(eq("basi"), anyList()))
                .thenReturn(List.of(pastLoan("basi", "book-cc"), pastLoan("basi", "book-1984")));
        when(loanRepository.findByBookIdAndStatusIn(eq("book-cc"), anyList()))
                .thenReturn(List.of(pastLoan("aarav", "book-cc"), pastLoan("basi", "book-cc")));
        when(reflectionRepository.findByLoanId(anyString())).thenReturn(Optional.empty());
        when(userRepository.findById("aarav")).thenReturn(Optional.of(aarav));

        List<RecommendationDto> result = recommendationService.recommendFor("aarav");

        RecommendationDto viaPeer = result.stream()
                .filter(r -> r.getBook().getId().equals("book-1984"))
                .findFirst().orElseThrow();
        assertThat(viaPeer.getReason())
                .contains("borrowed 'Clean Code'")
                .contains("also borrowed this");
    }

    // ── exclusions ──

    @Test
    void activeLoansAndQueueEntries_areExcluded() {
        stubBookLookups();
        Loan activeIssued = Loan.builder().id("l-act").studentId("aarav").bookId("book-pp")
                .status(LoanStatus.ISSUED).build();
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), eq(RecommendationService.READ_HISTORY_STATUSES)))
                .thenReturn(List.of(pastLoan("aarav", "book-cc")));
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), eq(RecommendationService.ACTIVE_EXCLUSION_STATUSES)))
                .thenReturn(List.of(activeIssued));
        when(loanRepository.findByStudentIdAndStatusIn(eq("basi"), anyList())).thenReturn(List.of());
        when(loanRepository.findByBookIdAndStatusIn(eq("book-cc"), anyList()))
                .thenReturn(List.of(pastLoan("aarav", "book-cc")));
        when(userRepository.findById("aarav")).thenReturn(Optional.of(aarav));

        List<RecommendationDto> result = recommendationService.recommendFor("aarav");

        assertThat(result).noneSatisfy(r -> assertThat(r.getBook().getId()).isEqualTo("book-pp"));
    }

    @Test
    void unreadBooks_prevailOverAlreadyReadOnes() {
        stubBookLookups();
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), eq(RecommendationService.READ_HISTORY_STATUSES)))
                .thenReturn(List.of(pastLoan("aarav", "book-cc"), pastLoan("aarav", "book-frk")));
        when(loanRepository.findByStudentIdAndStatusIn(eq("aarav"), eq(RecommendationService.ACTIVE_EXCLUSION_STATUSES)))
                .thenReturn(List.of());
        when(loanRepository.findByStudentIdAndStatusIn(eq("basi"), anyList())).thenReturn(List.of());
        when(loanRepository.findByBookIdAndStatusIn(eq("book-cc"), anyList()))
                .thenReturn(List.of(pastLoan("aarav", "book-cc")));
        when(loanRepository.findByBookIdAndStatusIn(eq("book-frk"), anyList())).thenReturn(List.of());
        when(userRepository.findById("aarav")).thenReturn(Optional.of(aarav));

        List<RecommendationDto> result = recommendationService.recommendFor("aarav");

        // book-1984 arrives only via similar-readers but Freakonomics (read) has subject affinity;
        // unread candidate exists (1984) so all-read candidates should be dropped
        assertThat(result).noneSatisfy(r -> assertThat(r.getBook().getId()).isEqualTo("book-frk"));
    }
}
