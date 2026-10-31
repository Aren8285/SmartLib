# SmartLib — University Library Management System

> **Iteration 1** — Core loan loop: request → issue → return.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 |
| Apache Maven | 3.x |
| Node.js | 18+ |
| npm | 9+ |
| MongoDB Atlas | any (free tier works) |

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

Backend starts on **http://localhost:8080**.

---

## 3. Start the Frontend

```bash
cd smartlib/frontend
npm install          # first time only
npm run dev
```

Frontend starts on **http://localhost:5173**.

---

## 4. Seed Data

On first startup the backend seeds:
- **2 users**: Aarav Student (`aarav@smartlib.test`) and Meera Librarian (`meera@smartlib.test`)
- **12 books** across Computer Science, Mathematics, Physics, Literature
- **Copies**: most books have 1–3 copies; *Discrete Mathematics* has **0 copies** (tests unavailable state)

---

## 5. REST API Endpoints

All routes are under `/api`.

### Users
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users` | List all users |

### Books
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/books` | List all books (with `availableCopies`) |
| GET | `/api/books?query=<text>` | Search by title / author / subject |
| GET | `/api/books/{id}` | Single book |

### Loans
| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/loans/request` | `{studentId, bookId}` | Student requests a book |
| GET | `/api/loans?studentId=<id>` | — | Student's loan history |
| GET | `/api/loans?status=REQUESTED` | — | All loans by status (for librarian) |
| POST | `/api/loans/{id}/issue` | `{librarianId}` | Librarian issues the loan |
| POST | `/api/loans/{id}/return` | `{librarianId}` | Librarian marks book returned |
| POST | `/api/loans/{id}/cancel` | `{studentId}` | Student cancels a REQUESTED loan |

### Error Responses
All errors return `{ timestamp, status, error, message }`.

---

## 6. Running Tests

```bash
cd smartlib/backend
mvn test
```

One test class (`LoanServiceHappyPathTest`) covers:
- Happy path: request → issue → return (copy status AVAILABLE → ISSUED → AVAILABLE)
- Duplicate active loan rejection (409)
- No copies available rejection (409)

---

## 7. Assumptions & Notes

- **No authentication**: role is determined by selecting a user from the navbar dropdown.
- **CORS**: backend allows only `http://localhost:5173`.
- **Due date**: set to **14 days** from issue date.
- **Copy reservation**: a copy is assigned only at issue time, not at request time (FIFO waitlist deferred to Iteration 2).
- **"Discrete Mathematics"** (0 copies) is the test case for the "Unavailable" button state.
- The data model includes all enums (`LoanStatus`, `CopyStatus`, `Role`) needed for future iterations so no migrations will be required.

