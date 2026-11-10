package com.smartlib.config;

import com.smartlib.model.*;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.FineRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.ReflectionRepository;
import com.smartlib.repository.ShelfRepository;
import com.smartlib.repository.UserRepository;
import com.smartlib.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo data: 12 shelves in a 4x3 floor grid, 60 books across 6 subjects,
 * 6 students + 2 librarians, and enough historical loans to make every
 * dashboard and report meaningful on first run. Seeding is automatic in dev
 * and gated by APP_SEED_DEMO in production.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "Password123";

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CopyRepository copyRepository;
    private final LoanRepository loanRepository;
    private final ReflectionRepository reflectionRepository;
    private final ShelfRepository shelfRepository;
    private final FineRepository fineRepository;
    private final WaitlistRepository waitlistRepository;
    private final PasswordEncoder passwordEncoder;

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Book> books = new LinkedHashMap<>();
    private final Map<String, List<Shelf>> shelvesBySection = new HashMap<>();
    private final Map<String, Integer> shelfCursor = new HashMap<>();

    @Override
    public void run(String... args) {
        boolean seeded = false;
        if (shelfRepository.count() == 0) {
            seedShelves();
            seeded = true;
        }
        if (userRepository.count() == 0) {
            seedUsers();
            seeded = true;
        }
        if (bookRepository.count() == 0) {
            loadShelves();
            seedBooksAndCopies();
            seedHistoricalActivity();
            seeded = true;
        }
        log.info("Seed check complete.");
        if (seeded) {
            log.info("Demo accounts — students: aarav@smartlib.test, diya@smartlib.test, "
                    + "kabir@smartlib.test, isha@smartlib.test, neha@smartlib.test, rohan@smartlib.test; "
                    + "librarians: meera@smartlib.test, ravi@smartlib.test — password: {}.", DEMO_PASSWORD);
        }
    }

    // ─────────── shelves ───────────

    private void seedShelves() {
        log.info("Seeding 12 shelves on a 4x3 floor grid...");
        String[][] defs = {
                {"A1", "Computer Science", "1", "1"},
                {"A2", "Computer Science", "1", "2"},
                {"A3", "Computer Science", "1", "3"},
                {"B1", "Mathematics", "2", "1"},
                {"B2", "Mathematics", "2", "2"},
                {"B3", "Physics", "2", "3"},
                {"C1", "Physics", "3", "1"},
                {"C2", "Literature", "3", "2"},
                {"C3", "Literature", "3", "3"},
                {"D1", "History", "4", "1"},
                {"D2", "History", "4", "2"},
                {"D3", "Economics", "4", "3"}
        };
        for (String[] def : defs) {
            shelfRepository.save(Shelf.builder()
                    .code(def[0])
                    .section(def[1])
                    .row(Integer.parseInt(def[2]))
                    .column(Integer.parseInt(def[3]))
                    .capacity(12)
                    .label(def[1] + " shelves")
                    .build());
        }
        loadShelves();
        log.info("Shelves seeded.");
    }

    private void loadShelves() {
        shelvesBySection.clear();
        shelfCursor.clear();
        for (Shelf shelf : shelfRepository.findAllByOrderByRowAscColumnAsc()) {
            shelvesBySection.computeIfAbsent(shelf.getSection(), k -> new ArrayList<>()).add(shelf);
        }
    }

    private Shelf nextShelf(String section) {
        List<Shelf> list = shelvesBySection.getOrDefault(section, List.of());
        if (list.isEmpty()) {
            return null;
        }
        int index = shelfCursor.getOrDefault(section, 0);
        shelfCursor.put(section, index + 1);
        return list.get(index % list.size());
    }

    // ─────────── users ───────────

    private void seedUsers() {
        log.info("Seeding users...");
        users.put("aarav", student("Aarav Sharma", "aarav@smartlib.test",
                List.of("Computer Science", "Mathematics"), 90));
        users.put("diya", student("Diya Patel", "diya@smartlib.test",
                List.of("Literature", "Physics"), 70));
        users.put("kabir", student("Kabir Singh", "kabir@smartlib.test",
                List.of("Mathematics", "Computer Science"), 50));
        users.put("isha", student("Isha Verma", "isha@smartlib.test",
                List.of("History", "Economics"), 30));
        users.put("neha", student("Neha Kapoor", "neha@smartlib.test",
                List.of("Computer Science", "Literature"), 40));
        users.put("rohan", student("Rohan Mehta", "rohan@smartlib.test",
                List.of("Physics", "History"), 20));

        users.put("meera", librarian("Meera Iyer", "meera@smartlib.test"));
        users.put("ravi", librarian("Ravi Deshpande", "ravi@smartlib.test"));
        log.info("Users seeded.");
    }

    private User student(String name, String email, List<String> preferredSubjects, int points) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .role(Role.STUDENT)
                .points(points)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .preferredSubjects(preferredSubjects)
                .createdAt(Instant.now().minus(60, ChronoUnit.DAYS))
                .active(true)
                .build());
    }

    private User librarian(String name, String email) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .role(Role.LIBRARIAN)
                .points(0)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .createdAt(Instant.now().minus(60, ChronoUnit.DAYS))
                .active(true)
                .build());
    }

    // ─────────── books & copies ───────────

    private void seedBooksAndCopies() {
        log.info("Seeding 60 books and their copies...");
        // Computer Science
        book("cs1", "Introduction to Algorithms", "Cormen, Leiserson, Rivest, Stein",
                "Computer Science", "9780262046305", 2);
        book("cs2", "Clean Code", "Robert C. Martin", "Computer Science", "9780132350884", 2);
        book("cs3", "The Pragmatic Programmer", "David Thomas, Andrew Hunt",
                "Computer Science", "9780135957059", 2);
        book("cs4", "Designing Data-Intensive Applications", "Martin Kleppmann",
                "Computer Science", "9781449373320", 2);
        book("cs5", "Refactoring", "Martin Fowler", "Computer Science", "9780134757599", 2);
        book("cs6", "The Mythical Man-Month", "Frederick P. Brooks Jr.",
                "Computer Science", "9780201835953", 2);
        book("cs7", "Structure and Interpretation of Computer Programs", "Harold Abelson, Gerald Jay Sussman",
                "Computer Science", "9780262510875", 3);
        book("cs8", "Computer Networks", "Andrew S. Tanenbaum, David J. Wetherall",
                "Computer Science", "9780132126953", 2);
        book("cs9", "Operating System Concepts", "Abraham Silberschatz, Peter B. Galvin, Greg Gagne",
                "Computer Science", "9781118063330", 2);
        book("cs10", "Artificial Intelligence: A Modern Approach", "Stuart Russell, Peter Norvig",
                "Computer Science", "9780136042594", 3);
        // Mathematics
        book("math1", "Calculus: Early Transcendentals", "James Stewart",
                "Mathematics", "9781285741550", 2);
        book("math2", "Linear Algebra and Its Applications", "Gilbert Strang",
                "Mathematics", "9780030105678", 1);
        book("math3", "Discrete Mathematics and Its Applications", "Kenneth Rosen",
                "Mathematics", "9780073383095", 0);
        book("math4", "Introduction to Probability", "Dimitri Bertsekas, John Tsitsiklis",
                "Mathematics", "9781886529236", 2);
        book("math5", "Concrete Mathematics", "Graham, Knuth, Patashnik",
                "Mathematics", "9780201558029", 1);
        book("math6", "Introduction to Linear Algebra", "Gilbert Strang",
                "Mathematics", "9780980232776", 2);
        book("math7", "Calculus, Vol. 1", "Tom M. Apostol",
                "Mathematics", "9780471000051", 2);
        book("math8", "A First Course in Probability", "Sheldon Ross",
                "Mathematics", "9780321815781", 2);
        book("math9", "Numerical Analysis", "Richard L. Burden, J. Douglas Faires",
                "Mathematics", "9781305253667", 1);
        book("math10", "Graph Theory", "Reinhard Diestel",
                "Mathematics", "9783662536216", 1);
        // Physics
        book("phy1", "University Physics", "Hugh Young, Roger Freedman",
                "Physics", "9780321973610", 2);
        book("phy2", "Concepts of Physics", "H.C. Verma", "Physics", "9788177091878", 2);
        book("phy3", "The Feynman Lectures on Physics", "Richard Feynman",
                "Physics", "9780465023820", 2);
        book("phy4", "Six Easy Pieces", "Richard Feynman", "Physics", "9780465025275", 2);
        book("phy5", "Astrophysics for People in a Hurry", "Neil deGrasse Tyson",
                "Physics", "9780393609394", 2);
        book("phy6", "Classical Mechanics", "Herbert Goldstein",
                "Physics", "9780201657029", 2);
        book("phy7", "Introduction to Quantum Mechanics", "David J. Griffiths",
                "Physics", "9781107189638", 3);
        book("phy8", "Thermodynamics and Statistical Mechanics", "Walter Greiner, Ludwig Neise, Horst Stöcker",
                "Physics", "9780387942698", 1);
        book("phy9", "Introduction to Electrodynamics", "David J. Griffiths",
                "Physics", "9781108420419", 2);
        book("phy10", "The Theoretical Minimum", "Leonard Susskind, George Hrabovsky",
                "Physics", "9780465075683", 2);
        // Literature
        book("lit1", "To Kill a Mockingbird", "Harper Lee", "Literature", "9780061120084", 2);
        book("lit2", "1984", "George Orwell", "Literature", "9780451524935", 2);
        book("lit3", "The Great Gatsby", "F. Scott Fitzgerald", "Literature", "9780743273565", 2);
        book("lit4", "Pride and Prejudice", "Jane Austen", "Literature", "9780141439518", 2);
        book("lit5", "The Catcher in the Rye", "J.D. Salinger", "Literature", "9780316769488", 2);
        book("lit6", "One Hundred Years of Solitude", "Gabriel García Márquez",
                "Literature", "9780060883287", 2);
        book("lit7", "Beloved", "Toni Morrison", "Literature", "9781400033416", 2);
        book("lit8", "The Remains of the Day", "Kazuo Ishiguro", "Literature", "9780679731726", 2);
        book("lit9", "Things Fall Apart", "Chinua Achebe", "Literature", "9780385474542", 2);
        book("lit10", "Midnight's Children", "Salman Rushdie", "Literature", "9780099578512", 1);
        // History
        book("his1", "A People's History of the United States", "Howard Zinn",
                "History", "9780060838652", 2);
        book("his2", "Sapiens: A Brief History of Humankind", "Yuval Noah Harari",
                "History", "9780062316097", 2);
        book("his3", "Guns, Germs, and Steel", "Jared Diamond", "History", "9780393317558", 2);
        book("his4", "The Silk Roads", "Peter Frankopan", "History", "9781101912379", 1);
        book("his5", "SPQR: A History of Ancient Rome", "Mary Beard", "History", "9781846683800", 2);
        book("his6", "The Discovery of India", "Jawaharlal Nehru", "History", "9780143031031", 2);
        book("his7", "Postwar: A History of Europe Since 1945", "Tony Judt",
                "History", "9780143037750", 2);
        book("his8", "The Making of the English Working Class", "E. P. Thompson",
                "History", "9780394703220", 1);
        book("his9", "India After Gandhi", "Ramachandra Guha", "History", "9780060958589", 2);
        book("his10", "The Warmth of Other Suns", "Isabel Wilkerson", "History", "9780679763888", 2);
        // Economics
        book("eco1", "An Inquiry into the Nature and Causes of the Wealth of Nations",
                "Adam Smith", "Economics", "9780553585971", 2);
        book("eco2", "Freakonomics", "Steven D. Levitt, Stephen J. Dubner",
                "Economics", "9780060731328", 2);
        book("eco3", "Thinking, Fast and Slow", "Daniel Kahneman", "Economics", "9780374533557", 2);
        book("eco4", "The Undercover Economist", "Tim Harford", "Economics", "9780199926510", 2);
        book("eco5", "Capital in the Twenty-First Century", "Thomas Piketty",
                "Economics", "9780674430006", 2);
        book("eco6", "Poor Economics", "Abhijit V. Banerjee, Esther Duflo",
                "Economics", "9781610390934", 2);
        book("eco7", "The Big Short", "Michael Lewis", "Economics", "9780393351590", 2);
        book("eco8", "Macroeconomics", "Olivier Blanchard", "Economics", "9780133780567", 2);
        book("eco9", "Intermediate Microeconomics", "Hal R. Varian", "Economics", "9780393689860", 2);
        book("eco10", "Development as Freedom", "Amartya Sen", "Economics", "9780385720274", 2);

        log.info("Books and copies seeded.");
    }

    private Book book(String key, String title, String author, String subject, String isbn, int copies) {
        Shelf shelf = nextShelf(subject);
        Book book = bookRepository.save(Book.builder()
                .title(title)
                .author(author)
                .subject(subject)
                .isbn(isbn)
                .shelfId(shelf == null ? null : shelf.getId())
                .createdAt(Instant.now().minus(90, ChronoUnit.DAYS))
                .build());
        books.put(key, book);
        for (int i = 0; i < copies; i++) {
            copyRepository.save(Copy.builder()
                    .bookId(book.getId())
                    .status(CopyStatus.AVAILABLE)
                    .condition("GOOD")
                    .shelfId(book.getShelfId())
                    .build());
        }
        return book;
    }

    // ─────────── historical activity ───────────

    private void seedHistoricalActivity() {
        log.info("Seeding historical loans, reflections, fines and queues...");
        Instant now = Instant.now();

        // Closed loans with approved reflections (recommendation + report signal).
        closedLoan(books.get("cs1"), users.get("aarav"), 34, 24, true);
        closedLoan(books.get("phy1"), users.get("aarav"), 28, 18, true);
        closedLoan(books.get("his2"), users.get("aarav"), 22, 14, true);
        closedLoan(books.get("cs2"), users.get("aarav"), 16, 7, true);
        closedLoan(books.get("lit2"), users.get("diya"), 30, 21, true);
        closedLoan(books.get("lit4"), users.get("diya"), 24, 16, true);
        closedLoan(books.get("eco2"), users.get("diya"), 18, 9, true);
        closedLoan(books.get("math1"), users.get("kabir"), 26, 17, true);
        closedLoan(books.get("cs3"), users.get("kabir"), 20, 12, false);
        closedLoan(books.get("his1"), users.get("isha"), 19, 11, true);
        closedLoan(books.get("cs4"), users.get("neha"), 15, 5, true);
        closedLoan(books.get("lit3"), users.get("rohan"), 12, 3, false);
        closedLoan(books.get("eco1"), users.get("aarav"), 9, 2, true);

        // Returned, awaiting a reflection (one already has a PENDING reflection).
        Loan kabirReflection = returnedLoan(books.get("math4"), users.get("kabir"), 10, 2,
                LoanStatus.REFLECTION_PENDING, false);
        Loan diyaReflection = returnedLoan(books.get("his4"), users.get("diya"), 12, 1,
                LoanStatus.REFLECTION_PENDING, false);
        returnedLoan(books.get("lit5"), users.get("neha"), 8, 1, LoanStatus.REFLECTION_PENDING, false);
        reflectionRepository.save(Reflection.builder()
                .studentId(users.get("diya").getId())
                .loanId(diyaReflection.getId())
                .bookId(diyaReflection.getBookId())
                .content("A sweeping history of trade routes; the chapter on the Silk Road's "
                        + "role in exchanging ideas, not just goods, changed how I think about "
                        + "globalisation.")
                .submissionDate(now.minus(20, ChronoUnit.HOURS))
                .status(ReflectionStatus.PENDING)
                .build());

        // Late/damaged returns with pending fines.
        Loan lateFineLoan = returnedLoan(books.get("eco3"), users.get("isha"), 20, 4,
                LoanStatus.FINE_PENDING, false);
        attachFine(lateFineLoan, users.get("isha"), 25, FineReason.LATE_RETURN,
                FineStatus.PENDING, 4, null, 25.0, null);
        Loan damagedLoan = returnedLoan(books.get("phy5"), users.get("rohan"), 30, 10,
                LoanStatus.FINE_PENDING, true);
        attachFine(damagedLoan, users.get("rohan"), 225, FineReason.DAMAGED_BOOK,
                FineStatus.PENDING, 10, null, 25.0, 200.0);

        // A paid fine on a closed loan (collected fines report).
        Loan paidFineLoan = closedLoan(books.get("his5"), users.get("isha"), 27, 12, true);
        attachFine(paidFineLoan, users.get("isha"), 10, FineReason.LATE_RETURN,
                FineStatus.PAID, 14, 8, 10.0, null);

        // Live loans.
        openLoan(books.get("cs5"), users.get("aarav"), LoanStatus.ISSUED, 3, 11);
        openLoan(books.get("phy2"), users.get("diya"), LoanStatus.ISSUED, 6, 8);
        openLoan(books.get("his3"), users.get("kabir"), LoanStatus.ISSUED, 1, 13);
        openLoan(books.get("math1"), users.get("isha"), LoanStatus.OVERDUE, 21, -6);
        openLoan(books.get("cs6"), users.get("neha"), LoanStatus.OVERDUE, 25, -3);
        openLoan(books.get("math5"), users.get("rohan"), LoanStatus.ISSUED, 8, 6);

        // Reserved copies waiting for pickup.
        readyLoan(books.get("phy3"), users.get("rohan"), 1, 30);
        readyLoan(books.get("eco4"), users.get("neha"), 0, 43);

        // Queues: a zero-copy book and the single-copy book that is checked out.
        waitlistedLoan(books.get("math3"), users.get("isha"), 3);
        waitlistedLoan(books.get("math3"), users.get("neha"), 2);
        waitlistedLoan(books.get("math5"), users.get("aarav"), 1);

        // Historic fulfilled queue entries (wait-time report).
        fulfilledEntry(closedLoanOf(books.get("cs1"), users.get("aarav")), 6, 5);
        fulfilledEntry(closedLoanOf(books.get("lit2"), users.get("diya")), 4, 3);
        fulfilledEntry(closedLoanOf(books.get("math1"), users.get("kabir")), 8, 6);

        log.info("Historical activity seeded: loans in CLOSED, REFLECTION_PENDING, FINE_PENDING, "
                + "ISSUED, OVERDUE, READY_FOR_PICKUP and WAITLISTED states.");
    }

    private Loan closedLoan(Book book, User student, int issuedDaysAgo, int returnedDaysAgo,
                            boolean approvedReflection) {
        Instant issued = Instant.now().minus(issuedDaysAgo, ChronoUnit.DAYS);
        Instant returned = Instant.now().minus(returnedDaysAgo, ChronoUnit.DAYS);
        Copy copy = takeAvailableCopy(book);
        Loan loan = loanRepository.save(Loan.builder()
                .studentId(student.getId())
                .bookId(book.getId())
                .copyId(copy == null ? null : copy.getId())
                .status(LoanStatus.CLOSED)
                .requestDate(issued.minus(2, ChronoUnit.HOURS))
                .issueDate(issued)
                .dueDate(issued.plus(14, ChronoUnit.DAYS))
                .returnDate(returned)
                .build());
        if (approvedReflection) {
            reflectionRepository.save(Reflection.builder()
                    .studentId(student.getId())
                    .loanId(loan.getId())
                    .bookId(book.getId())
                    .content("Reading \"" + book.getTitle() + "\" gave me a fresh perspective. "
                            + "The arguments were well paced and I could connect several chapters "
                            + "to my current coursework and future projects.")
                    .submissionDate(returned.plus(6, ChronoUnit.HOURS))
                    .status(ReflectionStatus.APPROVED)
                    .reviewedBy(users.get("meera") == null ? null : users.get("meera").getId())
                    .reviewedDate(returned.plus(1, ChronoUnit.DAYS))
                    .build());
        }
        return loan;
    }

    private Loan returnedLoan(Book book, User student, int issuedDaysAgo, int returnedDaysAgo,
                              LoanStatus status, boolean damagedCopy) {
        Instant issued = Instant.now().minus(issuedDaysAgo, ChronoUnit.DAYS);
        Instant returned = Instant.now().minus(returnedDaysAgo, ChronoUnit.DAYS);
        Copy copy = takeAvailableCopy(book);
        if (copy != null && damagedCopy) {
            copy.setStatus(CopyStatus.DAMAGED);
            copyRepository.save(copy);
        }
        return loanRepository.save(Loan.builder()
                .studentId(student.getId())
                .bookId(book.getId())
                .copyId(copy == null ? null : copy.getId())
                .status(status)
                .requestDate(issued.minus(2, ChronoUnit.HOURS))
                .issueDate(issued)
                .dueDate(issued.plus(14, ChronoUnit.DAYS))
                .returnDate(returned)
                .build());
    }

    private Loan openLoan(Book book, User student, LoanStatus status,
                          int issuedDaysAgo, int dueInDaysFromNow) {
        Instant issued = Instant.now().minus(issuedDaysAgo, ChronoUnit.DAYS);
        Copy copy = takeAvailableCopy(book);
        if (copy != null) {
            copy.setStatus(CopyStatus.ISSUED);
            copyRepository.save(copy);
        }
        return loanRepository.save(Loan.builder()
                .studentId(student.getId())
                .bookId(book.getId())
                .copyId(copy == null ? null : copy.getId())
                .status(status)
                .requestDate(issued.minus(2, ChronoUnit.HOURS))
                .issueDate(issued)
                .dueDate(Instant.now().plus(dueInDaysFromNow, ChronoUnit.DAYS))
                .build());
    }

    private Loan readyLoan(Book book, User student, int requestedDaysAgo, int pickupInHours) {
        Instant requested = Instant.now().minus(requestedDaysAgo, ChronoUnit.DAYS);
        Copy copy = takeAvailableCopy(book);
        if (copy != null) {
            copy.setStatus(CopyStatus.RESERVED);
            copyRepository.save(copy);
        }
        return loanRepository.save(Loan.builder()
                .studentId(student.getId())
                .bookId(book.getId())
                .copyId(copy == null ? null : copy.getId())
                .status(LoanStatus.READY_FOR_PICKUP)
                .requestDate(requested)
                .pickupDeadline(Instant.now().plus(pickupInHours, ChronoUnit.HOURS))
                .build());
    }

    private Loan waitlistedLoan(Book book, User student, int joinedDaysAgo) {
        Instant joined = Instant.now().minus(joinedDaysAgo, ChronoUnit.DAYS);
        Loan loan = loanRepository.save(Loan.builder()
                .studentId(student.getId())
                .bookId(book.getId())
                .status(LoanStatus.WAITLISTED)
                .requestDate(joined)
                .build());
        waitlistRepository.save(WaitlistEntry.builder()
                .bookId(book.getId())
                .studentId(student.getId())
                .loanId(loan.getId())
                .joinedAt(joined)
                .status(WaitlistStatus.WAITING)
                .build());
        return loan;
    }

    private void fulfilledEntry(Loan loan, int joinedDaysAgo, int fulfilledDaysAgo) {
        if (loan == null) return;
        waitlistRepository.save(WaitlistEntry.builder()
                .bookId(loan.getBookId())
                .studentId(loan.getStudentId())
                .loanId(loan.getId())
                .joinedAt(Instant.now().minus(joinedDaysAgo, ChronoUnit.DAYS))
                .fulfilledAt(Instant.now().minus(fulfilledDaysAgo, ChronoUnit.DAYS))
                .status(WaitlistStatus.FULFILLED)
                .build());
    }

    private Loan closedLoanOf(Book book, User student) {
        return loanRepository.findByStudentIdOrderByRequestDateDesc(student.getId()).stream()
                .filter(l -> l.getBookId().equals(book.getId()) && l.getStatus() == LoanStatus.CLOSED)
                .findFirst().orElse(null);
    }

    private void attachFine(Loan loan, User student, double amount, FineReason reason,
                            FineStatus status, int issuedDaysAgo, Integer paidDaysAgo,
                            Double lateAmount, Double damagedAmount) {
        Instant issued = Instant.now().minus(issuedDaysAgo, ChronoUnit.DAYS);
        Fine fine = fineRepository.save(Fine.builder()
                .studentId(student.getId())
                .loanId(loan.getId())
                .amount(amount)
                .reason(reason)
                .issueDate(issued)
                .paidDate(paidDaysAgo == null ? null : Instant.now().minus(paidDaysAgo, ChronoUnit.DAYS))
                .status(status)
                .lateAmount(lateAmount)
                .damagedAmount(damagedAmount)
                .build());
        loan.setFineId(fine.getId());
        loanRepository.save(loan);
    }

    private Copy takeAvailableCopy(Book book) {
        return copyRepository.findByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE).stream()
                .findFirst()
                .orElseGet(() -> copyRepository.save(Copy.builder()
                        .bookId(book.getId())
                        .status(CopyStatus.AVAILABLE)
                        .condition("GOOD")
                        .shelfId(book.getShelfId())
                        .build()));
    }
}
