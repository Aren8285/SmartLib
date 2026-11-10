# SmartLib API Reference

Base path: `/api`. All requests/responses are JSON unless noted. Protected routes need:

```http
Authorization: Bearer <JWT>
```

Public: `POST /api/auth/login`, `POST /api/auth/register`, `GET /actuator/health`.

Role rules are enforced server-side with method security. Students can only read/act on their own
loans, fines and reflections. Errors use:

```json
{
  "timestamp": "2026-11-06T14:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "This loan has already been returned or changed. Refresh and try again."
}
```

Reports and the leaderboard are described in §7. Dev-only endpoints are in §9.

---

## 1. Authentication

### `POST /api/auth/register` — public
Registers a **student**; role is always assigned server-side.

```json
{
  "name": "Demo Reader",
  "email": "demo.reader@smartlib.test",
  "password": "Password123",
  "preferredSubjects": ["Computer Science", "Mathematics"]
}
```

Validation: name 2–100 chars, valid unique email, password 8–100 with a letter and digit, ≤10 preferred
subjects. Response:

```json
{
  "token": "<jwt>",
  "user": {
    "id": "…", "name": "Demo Reader", "email": "demo.reader@smartlib.test",
    "role": "STUDENT", "points": 0, "preferredSubjects": ["Computer Science"],
    "createdAt": "2026-11-06T14:00:00Z"
  }
}
```

### `POST /api/auth/login` — public
```json
{ "email": "aarav@smartlib.test", "password": "Password123" }
```
Returns the same `AuthResponse`. Ten failed/any attempts per minute per client IP → **429** with
`Retry-After`; the limiter covers login and register.

### `GET /api/auth/me`
Returns the current `UserResponse`. Used to restore a session. Never includes a password hash.

---

## 2. Users, shelves and books

### Users
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/users` | LIBRARIAN | All users (no password hash) |
| GET | `/api/users/{id}` | authenticated | User detail |
| PUT | `/api/users/me` | authenticated | `{name, preferredSubjects}` |
| POST | `/api/users/librarian` | LIBRARIAN | `{name, email, password}` creates staff |

### Shelves
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/shelves` | authenticated | All shelves with `bookCount` |
| GET | `/api/shelves/{code}/books` | authenticated | Books on shelf code (e.g. `A2`) |
| POST | `/api/shelves` | LIBRARIAN | Create shelf |
| PUT | `/api/shelves/{id}` | LIBRARIAN | Update shelf |
| DELETE | `/api/shelves/{id}` | LIBRARIAN | Delete; **409** if it holds books |

Shelf response:

```json
{
  "id": "…", "code": "A2", "section": "Computer Science",
  "row": 1, "column": 2, "capacity": 12,
  "label": "Computer Science shelves", "bookCount": 2
}
```

Create/update body: `{code, section, row, column, capacity, label?}`. Codes are unique; a unique grid
position is required; capacity cannot be lower than the current book count.

### Books
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/books` | authenticated | Paginated catalogue |
| GET | `/api/books/{id}` | authenticated | Book detail |
| GET | `/api/books/subjects` | authenticated | Distinct subject names |
| POST / PUT / DELETE | `/api/books[/{id}]` | LIBRARIAN | Manage titles |
| PATCH | `/api/books/{id}/shelf` | LIBRARIAN | Assign `{shelfId}` (or null); capacity checked |
| GET | `/api/books/{id}/copies` | LIBRARIAN | Copies with holder/status |
| POST | `/api/books/{id}/copies` | LIBRARIAN | `{count, condition}` add copies |
| PATCH | `/api/copies/{id}/status` | LIBRARIAN | `{status: AVAILABLE\|DAMAGED\|LOST}` |

Catalogue query parameters: `query` (title/author/subject/ISBN), `subject`, `shelf` (shelf code), `availableOnly`, `page` (0-based), `size` (1–100,
default 12), `sort` (`title` default, `author`, `newest`). Response:

```json
{
  "items": [{
    "id": "…", "title": "Clean Code", "author": "Robert C. Martin",
    "subject": "Computer Science", "isbn": "9780132350884",
    "availableCopies": 2, "totalCopies": 2, "waitlistSize": 0,
    "shelfId": "…", "shelfSection": "Computer Science", "shelfCode": "A2"
  }],
  "page": 0, "size": 12, "totalItems": 30, "totalPages": 3
}
```

Book create/update body: `{title, author, subject, isbn?, shelfId?}`. Add-copies body:
`{count: 1-20, condition: "GOOD"|"WORN"|"DAMAGED"}`. Delete returns **409** when copies are held or
non-terminal loans exist.

---

## 3. Loans and waitlists

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/loans/request` | STUDENT | `{bookId}` → READY_FOR_PICKUP or WAITLISTED |
| GET | `/api/loans` | both | Page envelope; filters below |
| GET | `/api/loans/{id}` | owner / LIBRARIAN | Enriched loan |
| POST | `/api/loans/{id}/issue` | LIBRARIAN | Issue a reserved copy |
| POST | `/api/loans/{id}/return` | LIBRARIAN | `{bookCondition: "GOOD"\|"DAMAGED"}` optional |
| POST | `/api/loans/{id}/lost` | LIBRARIAN | Lost-book fine; copy not released |
| POST | `/api/loans/{id}/cancel` | STUDENT | While WAITLISTED or READY_FOR_PICKUP |
| POST | `/api/loans/{id}/renew` | STUDENT | Extend an owned ISSUED loan by the standard loan period |
| POST | `/api/loans/{id}/skip-reflection` | STUDENT | Close without points |

`GET /api/loans` parameters: `studentId` (`me` resolves to the token principal; librarians may pass an
id), `status` (comma-separated enum list, e.g. `ISSUED,OVERDUE`), `page`, `size`, and librarian-only
`q`. Desk lookup `q` matches student name/email, book title/author/subject/ISBN, or copy ID; it applies
when no `studentId` is given. Loans are sorted by `requestDate` descending and return the standard page
envelope.

Loan response (main fields):

```json
{
  "id": "…", "studentId": "…", "studentName": "Aarav Sharma",
  "bookId": "…", "bookTitle": "Clean Code", "copyId": "…",
  "status": "ISSUED", "requestDate": "…", "issueDate": "…", "dueDate": "…",
  "returnDate": null, "pickupDeadline": null, "queuePosition": null,
  "accruedFine": null, "fine": null, "renewalCount": 0, "renewalLimit": 2,
  "canRenew": true, "message": "Issued. Due back by Nov 20, 14:00."
}
```

**Renewal:** a student may renew their own ISSUED loan at most twice. Each renewal adds the standard loan
period to `dueDate`, resets the due reminder and creates a `RENEWED` notification. Renewal returns **409**
when the loan is overdue, the limit is reached, or a student is waiting for the book; `canRenew` reflects
these checks for display.

**Double actions:** issue/return/lost/cancel/pay/approve use conditional status updates. If another
request already changed the state, the second call returns **409** and does not repeat points/fines.

`GET /api/waitlist/book/{bookId}` (LIBRARIAN) lists the ordered WAITING queue with `position`,
`studentName`, `joinedAt` and `status`.

---

## 4. Fines and reflections

### Fines
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/fines?studentId=me` | STUDENT | Own fines |
| GET | `/api/fines?status=PENDING` | LIBRARIAN | Fines by status |
| GET | `/api/fines` | LIBRARIAN | All fines |
| POST | `/api/fines/{id}/pay` | STUDENT | Pay own fine |

Fine fields: `id`, `studentId`, `studentName`, `loanId`, `amount`, `reason`
(`LATE_RETURN|DAMAGED_BOOK|LOST_BOOK`), `issueDate`, `paidDate`, `status` (`PENDING|PAID`),
`lateAmount`, `damagedAmount`. Payment moves a late/damaged loan to REFLECTION_PENDING and a lost loan
to CLOSED.

### Reflections
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/reflections` | STUDENT | `{loanId, content}` for own REFLECTION_PENDING loan |
| GET | `/api/reflections/mine` | STUDENT | Own reflections |
| GET | `/api/reflections?status=PENDING` | LIBRARIAN | Review queue |
| POST | `/api/reflections/{id}/approve` | LIBRARIAN | +20 points exactly once; loan CLOSED |
| POST | `/api/reflections/{id}/reject` | LIBRARIAN | `{reviewNote}`; loan CLOSED, no points |

Reflection content is trimmed, 50–2000 characters; one per loan. `reviewNote` is required and ≤500 chars.

---

## 5. Notifications, recommendations and dashboards

| Method | Path | Description |
|---|---|---|
| GET | `/api/notifications` | Own notifications, newest first |
| GET | `/api/notifications/unread-count` | `{count}` |
| POST | `/api/notifications/{id}/read` | Mark one read |
| POST | `/api/notifications/read-all` | Mark all read |
| GET | `/api/recommendations` | Up to 8 personalised books, each with a reason |
| GET | `/api/dashboard/student` | Points, active/waitlisted counts, pending fines, next due, actions |
| GET | `/api/dashboard/librarian` | readyForPickup, issued, overdue, pendingReflections, pendingFines, totalBooks, totalCopies, copiesByStatus, oldestActionsNeeded |
| GET | `/api/leaderboard?limit=10` | Top students by points; `{rank, studentId, name, points}` |

Notification types include promotion, expiry, overdue, issued, renewal, pickup/due reminders, fine
imposed/paid, and reflection pending/approved/rejected. The bell polls every 30 seconds. The leaderboard
intentionally excludes email addresses.

---

## 6. Reports (LIBRARIAN only)

All period endpoints accept optional ISO dates `from` and `to`; default = last 30 days. Invalid range
(`from > to`) → **400**.

| Method | Path | Response |
|---|---|---|
| GET | `/api/reports/overdue` | `[{loanId, studentId, studentName, bookId, bookTitle, dueDate, daysLate, accruedFine}]` |
| GET | `/api/reports/popular-books?limit=10&from=&to=` | `[{rank, bookId, title, author, subject, borrowCount}]` |
| GET | `/api/reports/leaderboard?limit=10` | `[{rank, studentId, name, points}]` |
| GET | `/api/reports/circulation?from=&to=` | `{from, to, totalIssued, totalReturned, points:[{date, issued, returned}]}` |
| GET | `/api/reports/fines?from=&to=` | `{from, to, totalImposed, totalCollected, totalOutstanding, byReason:[{reason,count,imposed,collected,outstanding}]}` |
| GET | `/api/reports/waitlist?from=&to=` | `{from, to, overallAverageWaitHours, books:[{bookId,title,queueLength,averageWaitHours}]}` |
| GET | `/api/reports/inventory` | `{totalCopies, copiesByStatus:{...}, booksWithZeroAvailable:[{bookId,title,author,subject,isbn,totalCopies,availableCopies}]}` |
| GET | `/api/reports/export?type=…&from=&to=` | CSV attachment (`text/csv;charset=UTF-8`) |

Export types: `overdue`, `fines`, `popular`, `inventory`. Filename pattern:
`smartlib-<type>-YYYY-MM-DD.csv`. CSV cells are quote-escaped; the period endpoints append a
`# period …` footer to fines/popular files.

---

## 7. Dev-only endpoints (profile `dev`, LIBRARIAN)

| Method | Path | Description |
|---|---|---|
| POST | `/api/dev/loans/{id}/set-due-date?daysFromNow=-3` | Shift a loan due date |
| POST | `/api/dev/loans/{id}/set-pickup-deadline?minutesFromNow=-30` | Shift a pickup deadline |
| POST | `/api/dev/run-jobs` | Run expiry/overdue/reminder jobs; returns counts |
| GET | `/api/dev/invariants` | `{count, violations}` consistency check |
| POST | `/api/dev/repair` | Repair stuck queues; returns repaired count |

These endpoints do not exist in the `prod` profile. `GET /actuator/health` is public; all other actuator
endpoints are not exposed.

---

## 8. Security & operational headers

- Passwords are BCrypt-hashed; no response contains `passwordHash`.
- JWT is HS256, 480 minutes, secret ≥32 chars enforced at startup.
- Login/register: 10 attempts/minute/IP → 429 + `Retry-After`.
- Security headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: no-referrer`, HSTS, permissions policy.
- CORS origins come from `APP_CORS_ALLOWED_ORIGIN` (comma-separated), never a hard-coded production URL.
- Global exception handling returns readable 400/401/403/404/409/429 responses without stack traces.
