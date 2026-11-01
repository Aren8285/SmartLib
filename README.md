# SmartLib — University Library Management System

> **Iteration 2** — FIFO waitlist, pickup deadlines, overdue detection, fines, loyalty points, improved search.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 |
| Apache Maven | 3.x |
| Node.js | 18+ |
| npm | 9+ |
| MongoDB Atlas | any (free tier works) |

> **Important:** If you ran Iteration 1, **drop the old collections** (`users`, `books`, `copies`, `loans`) in the `smartlib` Atlas database before the first Iteration 2 run. The seeder only inserts when collections are empty, and the Iteration 1 seed (2 users) is superseded by the new 5-user seed.

---

## 1. Set `MONGODB_URI`

Create a free cluster at [cloud.mongodb.com](https://cloud.mongodb.com), then export the connection string:

```bash
export MONGODB_URI="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority"
```

> The database `smartlib` is created automatically on first run. Seed data is inserted once (when collections are empty).

---

## 2. Start the Backend

```bash
cd smartlib/backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # adjust for your system
mvn spring-boot:run
```

Backend starts on **http://localhost:8080** (Spring profile `dev` is the default, enabling the dev endpoints below).

## 3. Start the Frontend

```bash
cd smartlib/frontend
npm install          # first time only
npm run dev
```

Frontend starts on **http://localhost:5173**. Set `VITE_DEV_TOOLS=true` (see `frontend/.env.local`) to show the collapsible Dev tools panel on the librarian page.

---

## 4. Seed Data

On first startup the backend seeds:
- **5 users**: Aarav, Diya, Kabir, Isha (STUDENT) and Meera (LIBRARIAN, `meera@smartlib.test`)
- **12 books** across Computer Science, Mathematics, Physics, Literature
- **Copies**: most books have 1–3 copies; three books have exactly 1 copy; *Discrete Mathematics and Its Applications* has **0 copies** (always waitlisted)

---

## 5. Configuration (application.properties)

| Property | Default | Meaning |
|----------|---------|---------|
| `app.pickup-deadline-hours` | 48 | Hours until a READY_FOR_PICKUP reservation expires |
| `app.loan-days` | 14 | Loan length once issued |
| `app.fine-per-day` | 5 | Currency units per day overdue |
| `app.lost-book-fine` | 500 | Flat fine for losing a book |
| `app.on-time-return-points` | 10 | Loyalty points for an on-time return |
| `app.max-active-loans` | 3 | Max READY_FOR_PICKUP + ISSUED + OVERDUE loans per student |
| `app.max-waitlist-entries` | 5 | Max students queued per book |
| `app.scheduler.expiry-check-ms` | 60000 | Pickup-expiry job interval |
| `app.scheduler.overdue-check-ms` | 60000 | Overdue-detection job interval |

All values are overridable with `-Dapp.*=` at startup or per-profile properties files. Scheduling is enabled with `@EnableScheduling`.

---

## 6. Loan State Machine

```
                    ┌──────────── copy reserved atomically ────────────┐
 REQUEST ─┬─ copies free & no queue ──────────────► READY_FOR_PICKUP ── issue (now<=deadline) ──► ISSUED ─┬─ now<=due ──► RETURNED (+10 pts)
          │                                            │                                                  │
          └─ book has WAITING entries OR no copy ──► WAITLISTED                                         └─ now>due ───► FINE_PENDING (late fine)
                  (queue position = oldest-first)          │                                                        │ pay
                                                           │ cancel → CANCELLED / promote              RETURNED ◄───┘
        READY_FOR_PICKUP ─ deadline passed ──► EXPIRED                          (copy released/promotes next)
        ISSUED / OVERDUE ─ return ─ RETURNED | FINE_PENDING
        ISSUED / OVERDUE ─ mark lost ─ LOST → FINE_PENDING (₹500) ─ pay ─► CLOSED
```

Key invariants:
- **FIFO**: a new requester must join the queue if any WAITING entry exists for that book, even if a copy looks free.
- **Atomic reservation**: the request path uses a single `findAndModify` (`AVAILABLE → RESERVED`), so two simultaneous requests can never both get the last copy.
- **Copy release** is one shared routine (return, expiry, cancel-while-READY): promote the oldest WAITING student if any (copy stays RESERVED for them), otherwise set the copy AVAILABLE. Cancellation/removal lets remaining students move up because positions are *computed* from `joinedAt`, never stored.
- Overdue accrued fines are computed at read/return time (`ceil(days past due) × fine-per-day`), so no per-day mutation is needed.
- Multi-document writes are ordered loan-then-copy (safe sequencing); Atlas free tier (M0) does not support multi-document transactions.

## 7. REST API Endpoints

All routes are under `/api`. Errors return `{ timestamp, status, error, message }`.

### Users
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users` | List users (for the role switcher) |
| GET | `/api/users/{id}` | Single user, includes loyalty points |

### Books
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/books?query=&subject=&availableOnly=true` | Combined text / exact-subject / availability filter. Items include `availableCopies` and `waitlistSize` |
| GET | `/api/books/{id}` | Single book |
| GET | `/api/books/subjects` | Distinct subjects (filter dropdown) |

### Loans
| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/loans/request` | `{studentId, bookId}` | Reserve a copy (READY_FOR_PICKUP + pickup deadline) or join the waitlist (WAITLISTED + queuePosition) |
| GET | `/api/loans?studentId=<id>` | — | Student's loans, newest first |
| GET | `/api/loans?status=<STATUS>` | — | Loans by status, enriched with book title + student name |
| POST | `/api/loans/{id}/issue` | `{librarianId}` | Librarian hands over a reserved copy (fails after pickup deadline) |
| POST | `/api/loans/{id}/return` | `{librarianId}` | Return: on time → RETURNED +10 pts; late → FINE_PENDING with LATE_RETURN fine. Promotes next waitlisted student |
| POST | `/api/loans/{id}/lost` | `{librarianId}` | Mark lost: copy is NOT released, ₹500 fine, loan → FINE_PENDING, pay → CLOSED |
| POST | `/api/loans/{id}/cancel` | `{studentId}` | Student cancels while WAITLISTED or READY_FOR_PICKUP. Promotes the next student if a copy was held |

Enriched loan DTO fields: `bookTitle`, `studentName`, `queuePosition` (WAITLISTED), `pickupDeadline` (READY), `accruedFine` (OVERDUE), `fine` (when set).

### Fines
| Method | Path | Body | Description |
|--------|------|------|-------------|
| GET | `/api/fines?studentId=` or `?status=` | — | List fines |
| POST | `/api/fines/{id}/pay` | `{studentId}` | Simulated payment. Moves loan: LATE_RETURN → RETURNED, LOST_BOOK → CLOSED |

### Waitlist
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/waitlist/book/{bookId}` | Ordered FIFO queue with student names |

### Dev tools (Spring profile `dev`)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/dev/loans/{id}/set-due-date?daysFromNow=-2` | Shift an issued loan's due date (negative = past → overdue) |
| POST | `/api/dev/loans/{id}/set-pickup-deadline?minutesFromNow=-1` | Shift a reservation's deadline (negative = expired) |
| POST | `/api/dev/run-jobs` | Run the expiry + overdue jobs immediately |

## 8. Testing Tips (Dev Tools)

1. As Student A, request a 1-copy book → READY_FOR_PICKUP; Student B and C request the same book → WAITLISTED #1 and #2.
2. Librarian issues to A, returns it on time → A gets +10 points and B is promoted automatically.
3. In the Dev tools panel, push A's due date into the past and click **Run jobs now** → OVERDUE with a growing accrued fine; returning it creates a PENDING fine that blocks new requests until paid.
4. Push a promoted student's pickup deadline into the past and run jobs → their loan becomes EXPIRED and the next student is promoted.

## 9. Running Tests

```bash
cd smartlib/backend
mvn test
```

Service-level Mockito tests cover: reservation on free copy, waitlist join with computed position, FIFO promotion on return, dismissal-by-expiry promoting the next student (and copy → AVAILABLE with an empty queue), on-time points, late fine + FINE_PENDING → pay → RETURNED, lost fine, and rule violations (duplicate request, pending fines, max active loans, wrong role, invalid transitions).

## 10. Assumptions & Notes

- **No authentication**: role is determined by selecting a user from the navbar dropdown.
- **Currency symbol**: the UI displays ₹; adjust in the frontend if you use another currency.
- **RETURNED is terminal** in this iteration; Iteration 3 will route RETURNED → REFLECTION_PENDING → CLOSED.
- Atlas M0 free tier does not support multi-document transactions, so multi-document operations write in safe order (loan row first, copy/waitlist updates after) — see Section 6.
- Atlas data from Iteration 1 must be dropped before the first Iteration 2 run (Section 0 note at the top).
