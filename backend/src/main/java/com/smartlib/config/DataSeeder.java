package com.smartlib.config;

import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.ReflectionRepository;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;
    private final LoanRepository loanRepository;
    private final ReflectionRepository reflectionRepository;
    private final PasswordEncoder passwordEncoder;

    // Demo password for all seeded accounts.
    private static final String DEMO_PASSWORD = "Password123";

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            seedUsers();
        }
        if (bookRepository.count() == 0) {
            seedBooksAndCopies();
            seedHistoricalActivity();
        }
        log.info("Seed check complete.");
        log.info("Demo accounts (dev profile): aarav@smartlib.test, diya@smartlib.test, kabir@smartlib.test, isha@smartlib.test (students), meera@smartlib.test (librarian) — shared password: {}.", DEMO_PASSWORD);
    }

    private void seedUsers() {
        log.info("Seeding users...");
        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        userRepository.saveAll(List.of(
            student("Aarav Student", "aarav@smartlib.test", "Computer Science", "Mathematics"),
            student("Diya Student", "diya@smartlib.test", "Literature", "Physics"),
            student("Kabir Student", "kabir@smartlib.test", "Mathematics", "Computer Science"),
            student("Isha Student", "isha@smartlib.test", "History", "Economics"),
            User.builder().name("Meera Librarian").email("meera@smartlib.test").role(Role.LIBRARIAN)
                    .points(0).passwordHash(hash).createdAt(Instant.now()).active(true).build()
        ));
        log.info("Users seeded.");
    }

    private User student(String name, String email, String s1, String s2) {
        return User.builder().name(name).email(email).role(Role.STUDENT)
                .points(0)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .preferredSubjects(List.of(s1, s2))
                .createdAt(Instant.now())
                .active(true)
                .build();
    }

    private void seedBooksAndCopies() {
        log.info("Seeding books and copies...");

        // Computer Science — 3 books
        Book cs1 = bookRepository.save(Book.builder()
            .title("Introduction to Algorithms").author("Cormen, Leiserson, Rivest, Stein")
            .subject("Computer Science").isbn("9780262046305").build());

        Book cs2 = bookRepository.save(Book.builder()
            .title("Clean Code").author("Robert C. Martin")
            .subject("Computer Science").isbn("9780132350884").build());

        Book cs3 = bookRepository.save(Book.builder()
            .title("The Pragmatic Programmer").author("David Thomas, Andrew Hunt")
            .subject("Computer Science").isbn("9780135957059").build());

        // Mathematics — 3 books
        Book math1 = bookRepository.save(Book.builder()
            .title("Calculus: Early Transcendentals").author("James Stewart")
            .subject("Mathematics").isbn("9781285741550").build());

        Book math2 = bookRepository.save(Book.builder()
            .title("Linear Algebra and Its Applications").author("Gilbert Strang")
            .subject("Mathematics").isbn("9780030105678").build());

        Book math3 = bookRepository.save(Book.builder()
            .title("Discrete Mathematics and Its Applications").author("Kenneth Rosen")
            .subject("Mathematics").isbn("9780073383095").build());

        // Physics — 3 books
        Book phy1 = bookRepository.save(Book.builder()
            .title("University Physics").author("Hugh Young, Roger Freedman")
            .subject("Physics").isbn("9780321973610").build());

        Book phy2 = bookRepository.save(Book.builder()
            .title("Concepts of Physics").author("H.C. Verma")
            .subject("Physics").isbn("9789353435515").build());

        Book phy3 = bookRepository.save(Book.builder()
            .title("The Feynman Lectures on Physics").author("Richard Feynman")
            .subject("Physics").isbn("9780465023820").build());

        // Literature — 3 books
        Book lit1 = bookRepository.save(Book.builder()
            .title("To Kill a Mockingbird").author("Harper Lee")
            .subject("Literature").isbn("9780061120084").build());

        Book lit2 = bookRepository.save(Book.builder()
            .title("1984").author("George Orwell")
            .subject("Literature").isbn("9780451524935").build());

        Book lit3 = bookRepository.save(Book.builder()
            .title("The Great Gatsby").author("F. Scott Fitzgerald")
            .subject("Literature").isbn("9780743273565").build());

        // History — 2 books (variety for recommendations)
        Book his1 = bookRepository.save(Book.builder()
            .title("A People's History of the United States").author("Howard Zinn")
            .subject("History").isbn("9780060838652").build());
        Book his2 = bookRepository.save(Book.builder()
            .title("Sapiens: A Brief History of Humankind").author("Yuval Noah Harari")
            .subject("History").isbn("9780062316097").build());

        // Economics — 2 books
        Book eco1 = bookRepository.save(Book.builder()
            .title("An Inquiry into the Nature and Causes of the Wealth of Nations").author("Adam Smith")
            .subject("Economics").isbn("9780553585971").build());
        Book eco2 = bookRepository.save(Book.builder()
            .title("Freakonomics").author("Steven D. Levitt, Stephen J. Dubner")
            .subject("Economics").isbn("9780060731328").build());

        // Copies: plenty of test scenarios (1-copy books, one 0-copy book)
        copyRepository.saveAll(List.of(
            copy(cs1), copy(cs1),
            copy(cs2), copy(cs2),
            copy(cs3) // 1 copy
        ));
        copyRepository.saveAll(List.of(
            copy(math1), copy(math1),
            copy(math2) // 1 copy
            // math3: 0 copies — unavailable book for testing
        ));
        copyRepository.saveAll(List.of(
            copy(phy1), copy(phy1), copy(phy1),
            copy(phy2), copy(phy2),
            copy(phy3), copy(phy3)
        ));
        copyRepository.saveAll(List.of(
            copy(lit1), copy(lit1),
            copy(lit2), copy(lit2),
            copy(lit3)
        ));
        copyRepository.saveAll(List.of(
            copy(his1), copy(his1), copy(his2)
        ));
        copyRepository.saveAll(List.of(
            copy(eco1), copy(eco2), copy(eco2)
        ));

        log.info("Books and copies seeded.");
    }

    private Copy copy(Book book) {
        return Copy.builder().bookId(book.getId()).status(CopyStatus.AVAILABLE).build();
    }

    /**
     * Some finished reading for two students (+ approved reflections) so
     * recommendations and points have something to work with on first run.
     */
    private void seedHistoricalActivity() {
        var aarav = userRepository.findByEmail("aarav@smartlib.test").orElse(null);
        var diya = userRepository.findByEmail("diya@smartlib.test").orElse(null);
        if (aarav == null || diya == null) return;

        Book cs1 = bookRepository.findByIsbn("9780262046305");
        Book phy1 = bookRepository.findByIsbn("9780321973610");
        Book lit2 = bookRepository.findByIsbn("9780451524935");
        if (cs1 == null || phy1 == null || lit2 == null) return;

        seedClosedLoanWithReflection(aarav, cs1, "Great coverage of sorting and graph algorithms; the big-O walkthroughs finally made sense.", 20);
        seedClosedLoanWithReflection(aarav, phy1, "Mechanics chapter exercises were tough but very satisfying; great foils for lecture notes.", 20);
        seedClosedLoanWithReflection(diya, lit2, "Chillingly relevant and tight storytelling; the language still hits hard today.", 20);

        aarav.setPoints(10 + 20 + 10 + 20); // on-time points + reflection points for two loans
        diya.setPoints(10 + 20);
        userRepository.saveAll(List.of(aarav, diya));
        log.info("Historical loans + reflections seeded for Aarav and Diya.");
    }

    private void seedClosedLoanWithReflection(User student, Book book, String content, int reflectionPoints) {
        Instant now = Instant.now();
        Loan loan = loanRepository.save(Loan.builder()
                .studentId(student.getId())
                .bookId(book.getId())
                .status(LoanStatus.CLOSED)
                .requestDate(now.minus(40, ChronoUnit.DAYS))
                .issueDate(now.minus(38, ChronoUnit.DAYS))
                .dueDate(now.minus(24, ChronoUnit.DAYS))
                .returnDate(now.minus(26, ChronoUnit.DAYS))
                .build());
        reflectionRepository.save(Reflection.builder()
                .studentId(student.getId())
                .loanId(loan.getId())
                .bookId(book.getId())
                .content(content)
                .submissionDate(loan.getReturnDate())
                .status(ReflectionStatus.APPROVED)
                .reviewedDate(loan.getReturnDate() == null ? now : loan.getReturnDate().plus(2, ChronoUnit.DAYS))
                .build());
    }
}
