# Changes from the original proposal

This is an honest summary of where the final implementation differs from the original proposal and
diagrams. It is intended to help answer evaluator questions.

## Additions beyond the original scope

These features were added during Iterations 2–4 and are now part of the final system:

- **JWT authentication and two roles**, with server-side method security and ownership checks.
- **FIFO waitlist** with computed positions, atomic promotion and a queue-pressure report.
- **Pickup deadlines/expiry** (48 h) and expired-hold promotion.
- **Fines** for late returns, damaged books and lost books, including a combined late+damaged fine with
  a breakdown. Payments are simulated.
- **Loyalty points**, an on-time return reward and a student leaderboard.
- **Reading reflections** as the step that closes an on-time/paid loan; librarian approval/rejection with
  required review notes.
- **Personalised recommendations** and **in-app notifications** including one-shot pickup/due reminders.
- **Book/copy management**, damaged-copy repair and lost-copy handling.
- **Visual shelf map**, shelf CRUD/capacity rules and a librarian reports suite with CSV export.
- **Design system**, dark/light themes, accessibility work and responsive navigation.
- **Operational hardening**: rate limiting, security headers, conditional updates, invariant checker/
  repair, startup catch-up, retention cleanup and production profiles.
- **Production artifacts**: Dockerfiles, Compose, Render/Vercel guides and GitHub Actions CI.

## Where the design differs

### Scheduler and fine accrual
- The scheduler processes **pickup expiry, overdue flags and reminders** on intervals, but **fines are
  computed at read/return time** (`ceil(days late) × fine-per-day`) rather than by a separate daily
  accrual job. This keeps the amount consistent at the moment of return and avoids a write-amplifying job.
- Jobs are **idempotent per loan** via conditional updates; two instances can run safely, and an
  `ApplicationReadyEvent` hook catches up after free-tier sleep. Each loan's processing is wrapped in
  try/catch so one bad record cannot abort the batch.

### Reservations and shelves
- There is **no separate reservation entity**. A reservation is a loan in `READY_FOR_PICKUP` holding one
  `RESERVED` copy. This keeps the state machine and reports simpler.
- Shelf **capacity counts books (titles), not copies**. Copies inherit their book's `shelfId`; moving a
  book moves all its copies.
- The map is a **CSS-grid floor plan** with row/column coordinates, not a scaled architectural drawing.
  It models one floor (4×3 seed grid, expandable to 20×20 by DTO validation).
- A non-empty shelf cannot be deleted; a full shelf cannot receive another book.

### Reports
- Period reports default to the **last 30 days** when dates are omitted.
- Waitlist **queue length is current**, not limited to the selected period; the date range scopes the
  fulfilled-entry average wait.
- Popular-books book IDs are converted with `$toObjectId` before `$lookup` because loan/waitlist
  `bookId`s are strings while Mongo `_id`s are ObjectIds.
- Inventory "zero available" includes titles with no copies at all and titles whose copies are all held.

### Data consistency
- Atlas M0 (free tier) has **no multi-document transactions**. Multi-document operations write in a safe
  order (loan state first, then copy/queue), and the invariant checker/repair routine addresses stuck
  queues. The project does not claim ACID transactions across documents.
- The demo seed intentionally creates loans in many states; it only runs when collections are empty.
  On a reused database, old Iteration 3 data will not be re-seeded; use a fresh database name or drop the
  collections.

### Authentication and operations
- JWTs are stored in **localStorage** for demo simplicity. A hardened production app would prefer secure
  httpOnly cookies and refresh-token rotation.
- Rate limiting is an **in-memory fixed window** per instance; a multi-instance deployment should use a
  shared store (Redis) or edge rate limiting.
- CORS uses an explicit env-configured allowlist; wildcard origins are not used with credentials.
- Demo payments are simulated; **no payment gateway** is integrated.
- Notifications are **in-app only** (polled every 30 s); no email/SMS provider.

## Not built / deliberately out of scope

- Email or SMS notifications.
- Real payment processing/refunds.
- Multi-branch libraries or multiple floors.
- A separate mobile app.
- Playwright/Cypress E2E tests. The equivalent flows are covered by the gated Spring integration test
  (`FullLifecycleIT`) and the manual demo script. The spec marked browser E2E as optional if time-limited.
- Docker build/compose execution on the development machine (Docker was not installed). The files and
  Render instructions are included, but local Docker verification is marked NOT VERIFIED.

## Test profile

The default `mvn test` suite uses mocks and needs no database. A real-Mongo integration test exists as
`FullLifecycleIT` and is gated by `SMARTLIB_IT_URI`:

```bash
SMARTLIB_IT_URI="$MONGODB_URI" mvn -Dtest=FullLifecycleIT test
```

It drops the hard-coded `smartlib_it` database before/after each test and covers:
register → request → issue → return → reflect → approve, points/closed-state assertions, a second
approve rejection, and 20 concurrent requests for one last copy (exactly one READY, 19 WAITLISTED).
