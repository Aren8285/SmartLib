# SmartLib — 10–12 minute demo script

## Before you start

1. Backend running with the **dev** profile against a **fresh seeded database**:
   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=dev PORT=8080 \
   SPRING_DATA_MONGODB_DATABASE=smartlib4 \
   mvn spring-boot:run
   ```
2. Frontend running with demo controls:
   ```bash
   cd frontend
   VITE_DEV_TOOLS=true npm run dev
   ```
3. Open `http://localhost:5173` in a clean browser profile.
4. Accounts (password **`Password123`**):
   - Students: `aarav@`, `diya@`, `kabir@`, `isha@`, `neha@`, `rohan@` `smartlib.test`
   - Librarians: `meera@`, `ravi@` `smartlib.test`
5. Optional: keep two browser windows/profiles side by side — one student, one librarian.
6. Do not fail login more than 10 times per minute (rate limiter returns 429).

---

## 0:00 — Landing, theme and responsiveness (40 s)

1. Open `/` while logged out. Point out the SmartLib explanation: waitlist, points, recommendations, map.
2. Toggle **dark mode** in the top bar. Refresh; it persists.
3. Narrow the window to ~375 px; open the hamburger menu. Close it.
4. Click **Login**.

## 0:40 — Register a fresh student (60 s)

1. Click **Register**.
2. Enter:
   - Name: `Demo Reader`
   - Email: `demo.reader@smartlib.test`
   - Password: `Password123`
   - Preferred subjects: Computer Science, Mathematics
3. Submit. The dashboard opens at 0 points.
4. Point out the notification bell and the mobile-friendly nav.

## 1:40 — Search, shelf map and a reservation (100 s)

1. Go to **Browse books**.
2. Search `Clean Code`. Show the card/table toggle, filters, sorting and page controls.
3. The card shows **A2** and availability. Click **Locate on map** (or open `/map?shelf=A2`).
4. On the map, point out the 4×3 grid, colour-coded sections, legend and the pulsing **A2** shelf.
5. Click **A2**; the shelf's books and availability appear. Click the book to return, or use Back.
6. Request **Clean Code**. It is immediately **READY_FOR_PICKUP** with a 48-hour countdown.
   (If a copy is unavailable, the request joins the FIFO waitlist instead.)

## 3:20 — Librarian issues the book (40 s)

1. Log in as `meera@smartlib.test` in the second window.
2. **Circulation** → **Ready for pickup**: find *Demo Reader — Clean Code*.
3. Click **Issue**. The loan becomes **ISSUED** and moves to the issued section.
4. Point out the due date and the notification badge on the student side.

## 4:00 — Simulate time: overdue, fine, return (100 s)

1. On the librarian **Circulation** page, open the **Demo time controls** panel.
2. Select *Demo Reader — Clean Code*.
3. Enter `-3` in the number field next to **Set due date**, click **Set due date**.
4. Click **Run jobs now**. The loan becomes **OVERDUE**; the countdown/fine is visible.
5. Back in the issued table, click **Return** → condition **Good** → **Confirm return**.
   The loan becomes **FINE_PENDING** with a ₹15 late fine.
6. Point out that **Check invariants** still reports zero violations.

## 5:40 — Student pays and reflects (90 s)

1. Switch to the student window and refresh/go to **My fines**.
2. Show the fine breakdown, click **Pay fine**, confirm. The loan moves to **REFLECTION_PENDING**.
3. Go to **My loans → Awaiting reflection**; click **Write reflection**.
4. Enter at least 50 characters (counter turns valid), submit.
5. The notification and status show the reflection is pending librarian review.

## 7:10 — Librarian approves; points and leaderboard (70 s)

1. Librarian → **Reflections** → **Pending**.
2. Open *Demo Reader*'s reflection, click **Approve (+20 pts)** → confirm.
3. Student: dashboard points are now **20**; the loan is **CLOSED**; My loans history updates.
4. Open **Leaderboard**: top 10 names/points only, current student highlighted (or a "your rank" banner if
   outside the top 10). No emails are shown.

## 8:20 — Waitlist with a second student (80 s)

Use *Linear Algebra and Its Applications* (one copy, currently available).

1. Student Demo Reader → Browse → request **Linear Algebra…** → READY_FOR_PICKUP.
2. Log in as `diya@smartlib.test` in a third window (or log out/in). Request the same book.
   Diya is **WAITLISTED** with queue position #1.
3. Librarian → **Waitlists** → select the book: ordered queue with Diya at #1.
4. Librarian → **Circulation** → issue Demo Reader's copy, then immediately return it (good condition).
5. Diya's queue is promoted to **READY_FOR_PICKUP**; the notification bell shows the promotion.
   The copy is not left stranded.

> If time is short, stop after showing Diya's WAITLISTED position and the ordered queue.

## 9:40 — Reports and CSV (100 s)

1. Librarian → **Reports**.
2. Show the default last-30-days window; change **From/To** and click **Apply range**.
3. Point out:
   - Circulation **line chart** (issued/returned per day) with totals.
   - **Popular books** horizontal bar chart.
   - **Fines** donut + imposed/collected/outstanding table.
   - **Inventory** donut and titles with no available copies.
   - **Overdue**, **waitlist pressure** and **leaderboard** tables.
4. Click **CSV** on Overdue and Inventory; open the downloads and show the headers.
5. Return to Circulation, run **Check invariants** → zero violations.

## 11:20 — Wrap-up / optional shelf management (40 s)

1. Librarian → **Shelves**: show 12 shelves and book counts. (Optional: create `D4`, then try deleting a
   non-empty shelf and show the 409 message; delete the empty new shelf.)
2. Librarian → **Books & copies**: assign a book to a shelf and point out that the map updates.
3. Summarize: atomic reservations, FIFO promotion, conditional transitions, reports, hardened config,
   tests and deployment docs.

---

## Expected values on the seeded database

| Check | Expected |
|---|---|
| Shelves | 12 in a 4×3 grid |
| Books / copies | 30 / 55 |
| Overdue loans | 2 (Isha, Neha) |
| Fines imposed / outstanding | ₹260 / ₹250 |
| Pending fines | Isha ₹25, Rohan ₹225 |
| Waitlist pressure | Discrete Mathematics queue 2; Concrete Mathematics queue 1 |
| Invariants | 0 violations |
| Demo Reader after late fine + approved reflection | 20 points, loan CLOSED |

## Dev tools cheat sheet

- **Set due date**: `POST /api/dev/loans/{id}/set-due-date?daysFromNow=-3`
- **Set pickup deadline**: `POST /api/dev/loans/{id}/set-pickup-deadline?minutesFromNow=-1`
- **Run jobs**: `POST /api/dev/run-jobs`
- **Invariants**: `GET /api/dev/invariants` · **Repair**: `POST /api/dev/repair`

All are librarian-only and exist only in the `dev` profile.

## If something goes wrong

- **429 on login**: wait a minute or restart the backend; the limiter is in-memory.
- **Request blocked**: the student has a pending fine, 3 active loans, or already has an active loan/queue
  entry for that book. Use a different student.
- **No available copy**: request joins the waitlist; use a different title or promote by returning a copy.
- **Reports blank**: widen the date range; seeded history is mostly within 30 days.
- **No map highlight**: open `/map?shelf=A2` directly; codes are case-insensitive.
