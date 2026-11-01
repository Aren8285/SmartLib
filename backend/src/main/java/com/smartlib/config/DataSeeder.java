package com.smartlib.config;

import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            seedUsers();
        }
        if (bookRepository.count() == 0) {
            seedBooksAndCopies();
        }
        log.info("Seed check complete.");
    }

    private void seedUsers() {
        log.info("Seeding users...");
        userRepository.saveAll(List.of(
            User.builder().name("Aarav Student").email("aarav@smartlib.test").role(Role.STUDENT).build(),
            User.builder().name("Diya Student").email("diya@smartlib.test").role(Role.STUDENT).build(),
            User.builder().name("Kabir Student").email("kabir@smartlib.test").role(Role.STUDENT).build(),
            User.builder().name("Isha Student").email("isha@smartlib.test").role(Role.STUDENT).build(),
            User.builder().name("Meera Librarian").email("meera@smartlib.test").role(Role.LIBRARIAN).build()
        ));
        log.info("Users seeded.");
    }

    private void seedBooksAndCopies() {
        log.info("Seeding books and copies...");

        // Computer Science — 3 books
        Book cs1 = bookRepository.save(Book.builder()
            .title("Introduction to Algorithms").author("Cormen, Leiserson, Rivest, Stein")
            .subject("Computer Science").isbn("978-0262046305").build());

        Book cs2 = bookRepository.save(Book.builder()
            .title("Clean Code").author("Robert C. Martin")
            .subject("Computer Science").isbn("978-0132350884").build());

        Book cs3 = bookRepository.save(Book.builder()
            .title("The Pragmatic Programmer").author("David Thomas, Andrew Hunt")
            .subject("Computer Science").isbn("978-0135957059").build());

        // Mathematics — 3 books
        Book math1 = bookRepository.save(Book.builder()
            .title("Calculus: Early Transcendentals").author("James Stewart")
            .subject("Mathematics").isbn("978-1285741550").build());

        Book math2 = bookRepository.save(Book.builder()
            .title("Linear Algebra and Its Applications").author("Gilbert Strang")
            .subject("Mathematics").isbn("978-0030105678").build());

        Book math3 = bookRepository.save(Book.builder()
            .title("Discrete Mathematics and Its Applications").author("Kenneth Rosen")
            .subject("Mathematics").isbn("978-0073383095").build());

        // Physics — 3 books
        Book phy1 = bookRepository.save(Book.builder()
            .title("University Physics").author("Hugh Young, Roger Freedman")
            .subject("Physics").isbn("978-0321973610").build());

        Book phy2 = bookRepository.save(Book.builder()
            .title("Concepts of Physics").author("H.C. Verma")
            .subject("Physics").isbn("978-8177091878").build());

        Book phy3 = bookRepository.save(Book.builder()
            .title("The Feynman Lectures on Physics").author("Richard Feynman")
            .subject("Physics").isbn("978-0465023820").build());

        // Literature — 3 books
        Book lit1 = bookRepository.save(Book.builder()
            .title("To Kill a Mockingbird").author("Harper Lee")
            .subject("Literature").isbn("978-0061935466").build());

        Book lit2 = bookRepository.save(Book.builder()
            .title("1984").author("George Orwell")
            .subject("Literature").isbn("978-0451524935").build());

        Book lit3 = bookRepository.save(Book.builder()
            .title("The Great Gatsby").author("F. Scott Fitzgerald")
            .subject("Literature").isbn("978-0743273565").build());

        // Copies: at least 2 books with exactly 1 copy, 1 book with 0 copies
        // cs1: 3 copies
        copyRepository.saveAll(List.of(
            Copy.builder().bookId(cs1.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(cs1.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(cs1.getId()).status(CopyStatus.AVAILABLE).build()
        ));
        // cs2: 2 copies
        copyRepository.saveAll(List.of(
            Copy.builder().bookId(cs2.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(cs2.getId()).status(CopyStatus.AVAILABLE).build()
        ));
        // cs3: 1 copy (single copy book #1)
        copyRepository.save(Copy.builder().bookId(cs3.getId()).status(CopyStatus.AVAILABLE).build());

        // math1: 2 copies
        copyRepository.saveAll(List.of(
            Copy.builder().bookId(math1.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(math1.getId()).status(CopyStatus.AVAILABLE).build()
        ));
        // math2: 1 copy (single copy book #2)
        copyRepository.save(Copy.builder().bookId(math2.getId()).status(CopyStatus.AVAILABLE).build());
        // math3: 0 copies — unavailable book for testing
        // (no copies saved for math3)

        // phy1: 3 copies
        copyRepository.saveAll(List.of(
            Copy.builder().bookId(phy1.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(phy1.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(phy1.getId()).status(CopyStatus.AVAILABLE).build()
        ));
        // phy2: 2 copies
        copyRepository.saveAll(List.of(
            Copy.builder().bookId(phy2.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(phy2.getId()).status(CopyStatus.AVAILABLE).build()
        ));
        // phy3: 1 copy
        copyRepository.save(Copy.builder().bookId(phy3.getId()).status(CopyStatus.AVAILABLE).build());

        // lit1: 2 copies
        copyRepository.saveAll(List.of(
            Copy.builder().bookId(lit1.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(lit1.getId()).status(CopyStatus.AVAILABLE).build()
        ));
        // lit2: 2 copies
        copyRepository.saveAll(List.of(
            Copy.builder().bookId(lit2.getId()).status(CopyStatus.AVAILABLE).build(),
            Copy.builder().bookId(lit2.getId()).status(CopyStatus.AVAILABLE).build()
        ));
        // lit3: 1 copy
        copyRepository.save(Copy.builder().bookId(lit3.getId()).status(CopyStatus.AVAILABLE).build());

        log.info("Books and copies seeded.");
    }
}

