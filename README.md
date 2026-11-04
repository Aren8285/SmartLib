# SmartLib — University Library Management System

> **Iteration 3** — JWT authentication, reading reflections completing the loan lifecycle,
> personalised recommendations, in-app notifications, book/copy management, dashboards,
> damaged-book handling, and a role-based React frontend.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 |
| Apache Maven | 3.x |
| Node.js | 18+ |
| npm | 9+ |
| MongoDB Atlas | any (free tier works) |

> **Important:** Use a **fresh database** (or drop the old collections) before the first
> Iteration 3 run — e.g. export `SPRING_DATA_MONGODB_DATABASE=smartlib3`. The seeder only
> inserts when collections are empty, and the Iteration 2 data (no password hashes) would
> break JWT logins.

---

## 1. Environment Variables

From `/Users/aren/Projects/MiniProj/.env` (or your shell):

```bash
export MONGODB_URI="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority"
export JWT_SECRET="<at-least-32-characters-long-random-string>"
```

- **`MONGODB_URI`** — required; startup fails without it.
- **`JWT_SECRET`** — required; the backend **refuses to start** if it is missing or shorter
  than 32 characters. Tokens are HS256, valid for 480 minutes.

Running with a separate test database (keeps Iteration 2 data untouched):

```bash
cd smartlib/backend
SPRING_DATA_MONGODB_DATABASE=smartlib3 \
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
/opt/homebrew/bin/mvn spring-boot:run
```

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

Frontend starts on **http://localhost:5173**. Set `VITE_DEV_TOOLS=true` to show the Dev
sandbox panel on the librarian Circulation page.

---

## 4. Seed Data

On first startup the backend seeds:
- **5 users**: Aarav, Diya, Kabir, Isha (STUDENT) and Meera (LIBRARIAN, `meera@smartlib.test`), password `Password123`, with preferred subjects
- **16 books / 6 subjects** (Computer Science, Mathematics, Physics, Literature, History, Economics) with a healthy copy mix
- **Historical CLOSED loans + APPROVED reflections** for Aarav (30 pts) and Diya (20 pts) so recommendations have real signals
- Demo accounts are logged in the dev log for convenience

All passwords are BCrypt-hashed; the seeder is the only way to get an account except
`POST /api/auth/register` (students) and `POST /api/users/librarian` (librarian-only creation
of additional librarians).

---

## 5. Authentication

- `POST /api/auth/register` — students only; email must be unique, password ≥ 8 chars with at least one letter and one digit; returns `{token, user}`
- `POST /api/auth/login` — email + password; returns `{token, user}` (role-dependent landing page)
- `GET /api/auth/me` — current user (used to restore a session)
- **Every route under `/api` now requires `Authorization: Bearer <token>`** — 401 JSON otherwise
- Librarian-only endpoints enforce roles via method security; 403 JSON otherwise
- Identity always comes from the JWT principal (`@AuthenticationPrincipal`), never from the
  request body: students can only ever see and act on their own loans/fines/reflections
- 401/403 return the same `{timestamp, status, error, message}` JSON shape as other errors

## 6. Configuration (application.properties)

| Property | Default | Meaning |
|----------|---------|---------|
| `app.jwt.secret` | `${JWT_SECRET}` | HS256 signing key (≥32 chars enforced) |
| `app.jwt.expiration-minutes` | 480 | Token lifetime |
| `app.reflection-points` | 20 | Points awarded when a reflection is approved |
| `app.reflection-min-length` | 50 | Minimum characters per reflection |
| `app.recommendation-limit` | 8 | Max recommended books per student |
| `app.damaged-book-fine` | 200 | Flat fine when a copy is returned damaged |
| `app.pickup-deadline-hours` | 48 | Hours until a READY_FOR_PICKUP reservation expires |
| `app.loan-days` | 14 | Loan length once issued |
| `app.fine-per-day` | 5 | Currency units per day overdue |
| `app.lost-book-fine` | 500 | Flat fine for losing a book |
| `app.on-time-return-points` | 10 | Loyalty points for an on-time return |
| `app.max-active-loans` | 3 | Max READY_FOR_PICKUP + ISSUED + OVERDUE loans per student |
| `app.max-waitlist-entries` | 5 | Max students queued per book |
| `app.scheduler.*-check-ms` | 60000 / 300000 | Job intervals (overrides/expiry/overdue + reminders) |

## 7. Loan State Machine

```
 REQUEST ──► READY_FOR_PICKUP (pickup deadline) ── issue ──► ISSUED (due date) ─┬─ on-time return ──► REFLECTION_PENDING ──► PENDING → APPROVED → CLOSED (+20)
              │ deadline passed ──► EXPIRED                                    │                                          │ skip → CLOSED (no points)
              └ cancel → copy released / promote                              ├─ late (days) → accrues per-day fine
                                                                              │   return → FINE_PENDING (₹days×5)
                                                                              └─ return damaged → FINE_PENDING (₹200 + ₹late)
        FINE_PENDING ── pay ──► REFLECTION_PENDING (late/damaged) or CLOSED (lost)
        LOST_BOOK: copy NOT released; pay → CLOSED
        Waitlisted: FIFO queue, computed positions; every release promotes the oldest WAITING student
```

Combined damaged+late fines are stored on **one fine** with `lateAmount` / `damagedAmount`
breakdown fields. Reflections are one per loan, min 50 chars; approve awards exactly +20 via
atomic `$inc` (double-approve impossible); reject requires a review note. Skipping closes the
lifecycle without points.

Key invariants (unchanged from Iteration 2):
- **FIFO** queue respected on request and promotion; positions computed from `joinedAt`, never stored
- **Atomic copy reservation** (`findAndModify AVAILABLE → RESERVED`) — no double-booking
- One shared copy-release routine (return / expiry / cancel), always promoting waiters
- Multi-document writes are ordered loan-then-copy (Atlas M0 has no transactions)

## 8. REST API Endpoints

All routes under `/api`, all requiring a Bearer token except none. Errors: `{timestamp, status, error, message}`.

### Auth
| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | `{name, email, password, preferredSubjects}` | Student registration → `{token, user}` |
| POST | `/api/auth/login` | `{email, password}` | → `{token, user}` |
| GET | `/api/auth/me` | — | Current user from token |

### Users
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users` | Librarian only. All users incl. points |
| POST | `/api/users/librarian` | Librarian only. `{name, email, password}` |
| GET | `/api/users/me` | Own profile |
| PUT | `/api/users/me` | Update own name / preferredSubjects |

### Books & copies
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/books?query=&subject=&availableOnly=true` | Search; items include `availableCopies`, `totalCopies`, `waitlistSize` |
| GET | `/api/books/{id}` · `/api/books/subjects` | Detail · distinct subjects |
| POST | `/api/books` · PUT/DELETE `/api/books/{id}` | Librarian only. Delete blocked (409) while copies are RESERVED/ISSUED or loans are non-terminal |
| GET | `/api/books/{id}/copies` · POST | Librarian only. List copies (with current holder) · add copies (runs the release/promote routine) |
| PATCH | `/api/copies/{id}/status` | Librarian only. AVAILABLE↔DAMAGED, AVAILABLE→LOST; RESERVED/ISSUED is 409 |

### Loans
| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/loans/request` | `{bookId}` | Reserve a copy or join the waitlist (identity from token) |
| GET | `/api/loans?studentId=me` | — | Own loans (students cannot query other ids: 403) |
| GET | `/api/loans/{id}` | — | Own loan (librarian may read any) |
| GET | `/api/loans?status=` | — | Librarian only |
| POST | `/api/loans/{id}/issue` | — | Librarian only |
| POST | `/api/loans/{id}/return` | `{bookCondition?}\|"GOOD"` | On time → REFLECTION_PENDING (+10); late → FINE_PENDING; damaged → FINE_PENDING with combined fine, copy → DAMAGED (not released) |
| POST | `/api/loans/{id}/lost` | — | Librarian only. Copy NOT released, ₹500 fine |
| POST | `/api/loans/{id}/cancel` | — | Student, while WAITLISTED/READY_FOR_PICKUP |
| POST | `/api/loans/{id}/skip-reflection` | — | Student, while REFLECTION_PENDING → CLOSED |

### Reflections
| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/reflections` | `{loanId, content}` | Own loan, min 50 chars, one per loan |
| GET | `/api/reflections/mine` | — | Own reflections |
| GET | `/api/reflections?status=` | — | Librarian only |
| POST | `/api/reflections/{id}/approve` | — | Librarian. +20 points exactly once (atomic `$inc`) |
| POST | `/api/reflections/{id}/reject` | `{reviewNote}` | Librarian. Note required |

### Fines
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/fines?studentId=me` or `?status=` | List fines (students see only their own) |
| POST | `/api/fines/{id}/pay` | Pay (own fine). Loan → REFLECTION_PENDING (late/damaged) or CLOSED (lost) |

### Notifications
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/notifications` | Own notifications, newest first |
| GET | `/api/notifications/unread-count` | `{count}` |
| POST | `/api/notifications/{id}/read` · `/read-all` | Mark read |

Notification types: promotion, reservation expiry, overdue, issued, pickup reminder (<6h, one-shot), due reminder (<24h, one-shot), fine imposed/paid, reflection awaiting review / approved / rejected.

### Recommendations & dashboards
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/recommendations` | Personalised, max 8. Scoring: subject affinity (loans +1.0/pair, +1.0 per approved reflection, preferredSubjects +0.5), peer signal +0.1/loan of read-alike students, availability +0.3 as tiebreak. Every item carries a human reason; cold-start falls back to popularity |
| GET | `/api/dashboard/student` | Points, active/waitlisted counts, pending fines, next due date, actions needed |
| GET | `/api/dashboard/librarian` | Circulation counts, copies by status, oldest loans needing action |

### Dev tools (profile `dev`, librarian-only)
`POST /api/dev/loans/{id}/set-due-date?daysFromNow=` · `/set-pickup-deadline?minutesFromNow=` · `POST /api/dev/run-jobs`

## 9. Testing Tips (Dev Sandbox)

With `VITE_DEV_TOOLS=true` the Circulation page shows per-loan buttons: push a due date into
the past (−5d) or a pickup deadline past (−2h), then **Run jobs now** to exercise reminders,
expiry, promotion and overdue in one click.

## 10. Running Tests

```bash
cd smartlib/backend
mvn test
```

67 unit tests across LoanServiceStateMachineTest, WaitlistServiceTest, FineServiceTest,
AuthServiceTest, ReflectionServiceTest, RecommendationServiceTest and
BookServiceManagementTest.

## 11. Assumptions & Notes

- **No email/SMS**: all notifications are in-app, polled every 30s by the frontend bell.
- **Currency symbol**: the UI displays ₹; adjust in the frontend if needed.
- Atlas M0 free tier does not support multi-document transactions, so multi-document
  operations write in safe order (loan first, then copy/waitlist updates) — see Section 7.
- Test runs used a separate `smartlib3` Atlas database (`SPRING_DATA_MONGODB_DATABASE`),
  so Iteration 2 data stays untouched; drop `smartlib3` if you want a fresh seed.
- DELETE a book only removes the book + copy records; historical loan documents keep their
  titles because loan DTOs snapshot `bookTitle`/`studentName`.
