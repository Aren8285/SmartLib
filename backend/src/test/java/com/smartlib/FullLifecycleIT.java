package com.smartlib;

import com.smartlib.dto.AuthResponse;
import com.smartlib.dto.LoanRequestDto;
import com.smartlib.dto.LoanResponse;
import com.smartlib.dto.ReflectionDto;
import com.smartlib.dto.RegisterDto;
import com.smartlib.dto.SubmitReflectionDto;
import com.smartlib.model.Book;
import com.smartlib.model.Copy;
import com.smartlib.model.CopyStatus;
import com.smartlib.model.Loan;
import com.smartlib.model.LoanStatus;
import com.smartlib.model.ReflectionStatus;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.model.WaitlistStatus;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import com.smartlib.service.AuthService;
import com.smartlib.service.LoanService;
import com.smartlib.service.ReflectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Mongo integration test (not part of the default `mvn test` run because it
 * needs a MongoDB URI). Run against a disposable database:
 *
 *   SMARTLIB_IT_URI="$MONGODB_URI" mvn -Dtest=FullLifecycleIT test
 *
 * The test drops the configured database before and after each test. Never point
 * SMARTLIB_IT_URI at a database you care about.
 */
@SpringBootTest(properties = {
        "spring.data.mongodb.uri=${SMARTLIB_IT_URI}",
        "spring.data.mongodb.database=smartlib_it",
        "app.seed-demo=false",
        "app.max-waitlist-entries=50",
        "app.rate-limit.enabled=false",
        "app.jwt.secret=integration-test-secret-0123456789abcdef"
})
@EnabledIfEnvironmentVariable(named = "SMARTLIB_IT_URI", matches = ".+")
class FullLifecycleIT {

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private AuthService authService;
    @Autowired
    private LoanService loanService;
    @Autowired
    private ReflectionService reflectionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CopyRepository copyRepository;
    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private WaitlistRepository waitlistRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        mongoTemplate.getDb().drop();
    }

    @Test
    void registerRequestIssueReturnReflectApproveAndAwardPointsExactlyOnce() {
        RegisterDto registration = new RegisterDto();
        registration.setName("Integration Reader");
        registration.setEmail("integration.reader@smartlib.test");
        registration.setPassword("Password123");
        registration.setPreferredSubjects(List.of("Computer Science"));
        AuthResponse auth = authService.register(registration);
        String studentId = auth.getUser().getId();
        assertThat(auth.getToken()).isNotBlank();

        User librarian = userRepository.save(User.builder()
                .name("Integration Librarian")
                .email("integration.librarian@smartlib.test")
                .role(Role.LIBRARIAN)
                .points(0)
                .passwordHash(passwordEncoder.encode("Password123"))
                .createdAt(Instant.now())
                .active(true)
                .build());

        Book book = bookRepository.save(Book.builder()
                .title("Integration Engineering")
                .author("Test Author")
                .subject("Computer Science")
                .createdAt(Instant.now())
                .build());
        Copy copy = copyRepository.save(Copy.builder()
                .bookId(book.getId())
                .status(CopyStatus.AVAILABLE)
                .condition("GOOD")
                .build());

        LoanRequestDto request = new LoanRequestDto();
        request.setBookId(book.getId());
        LoanResponse ready = loanService.requestLoan(studentId, request);
        assertThat(ready.getStatus()).isEqualTo(LoanStatus.READY_FOR_PICKUP);
        assertThat(ready.getCopyId()).isEqualTo(copy.getId());

        LoanResponse issued = loanService.issueLoan(ready.getId());
        assertThat(issued.getStatus()).isEqualTo(LoanStatus.ISSUED);
        assertThat(issued.getDueDate()).isNotNull();

        LoanResponse returned = loanService.returnLoan(issued.getId(), null);
        assertThat(returned.getStatus()).isEqualTo(LoanStatus.REFLECTION_PENDING);
        assertThat(userRepository.findById(studentId).orElseThrow().getPoints()).isEqualTo(10);

        SubmitReflectionDto submission = new SubmitReflectionDto();
        submission.setLoanId(issued.getId());
        submission.setContent("This integration test reflection is deliberately long enough "
                + "to satisfy the minimum length and complete the loan lifecycle.");
        ReflectionDto reflection = reflectionService.submit(studentId, submission);
        assertThat(reflection.getStatus()).isEqualTo(ReflectionStatus.PENDING);

        ReflectionDto approved = reflectionService.approve(reflection.getId(), librarian.getId());
        assertThat(approved.getStatus()).isEqualTo(ReflectionStatus.APPROVED);

        Loan closed = loanRepository.findById(issued.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(LoanStatus.CLOSED);
        assertThat(userRepository.findById(studentId).orElseThrow().getPoints()).isEqualTo(30);
        assertThat(copyRepository.findById(copy.getId()).orElseThrow().getStatus())
                .isEqualTo(CopyStatus.AVAILABLE);

        // A second approve must be rejected and must not add the 20 points again.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> reflectionService.approve(reflection.getId(), librarian.getId()))
                .isInstanceOf(com.smartlib.exception.BusinessRuleException.class)
                .hasMessageContaining("Only PENDING reflections");
        assertThat(userRepository.findById(studentId).orElseThrow().getPoints()).isEqualTo(30);
    }

    @Test
    void twentyConcurrentRequestsForTheLastCopyReserveExactlyOneAndQueueTheRest() throws Exception {
        Book book = bookRepository.save(Book.builder()
                .title("The Last Copy")
                .author("Concurrency Author")
                .subject("Computer Science")
                .createdAt(Instant.now())
                .build());
        Copy copy = copyRepository.save(Copy.builder()
                .bookId(book.getId())
                .status(CopyStatus.AVAILABLE)
                .condition("GOOD")
                .build());

        String sharedHash = passwordEncoder.encode("Password123");
        List<String> studentIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            User student = userRepository.save(User.builder()
                    .name("Racer " + i)
                    .email("racer" + i + "@smartlib.test")
                    .role(Role.STUDENT)
                    .points(0)
                    .passwordHash(sharedHash)
                    .createdAt(Instant.now())
                    .active(true)
                    .build());
            studentIds.add(student.getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(studentIds.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<LoanResponse>> futures = new ArrayList<>();
        try {
            for (String studentId : studentIds) {
                futures.add(pool.submit(() -> {
                    start.await();
                    LoanRequestDto dto = new LoanRequestDto();
                    dto.setBookId(book.getId());
                    return loanService.requestLoan(studentId, dto);
                }));
            }
            start.countDown();

            long ready = 0;
            long waitlisted = 0;
            for (Future<LoanResponse> future : futures) {
                LoanResponse response = future.get();
                if (response.getStatus() == LoanStatus.READY_FOR_PICKUP) ready++;
                if (response.getStatus() == LoanStatus.WAITLISTED) waitlisted++;
            }
            assertThat(ready).isEqualTo(1);
            assertThat(waitlisted).isEqualTo(19);
        } finally {
            pool.shutdownNow();
        }

        assertThat(copyRepository.findById(copy.getId()).orElseThrow().getStatus())
                .isEqualTo(CopyStatus.RESERVED);
        assertThat(loanRepository.findByStatus(LoanStatus.READY_FOR_PICKUP)).hasSize(1);
        assertThat(waitlistRepository.countByBookIdAndStatus(book.getId(), WaitlistStatus.WAITING))
                .isEqualTo(19);
    }
}
