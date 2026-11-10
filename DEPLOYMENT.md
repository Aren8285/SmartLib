# Deploying SmartLib

This guide deploys:
- **MongoDB Atlas** — managed database,
- **Render** — Spring Boot backend (Docker or native Java),
- **Vercel** — React/Vite frontend.

The repository root is `smartlib/` (it contains `backend/` and `frontend/`). Do not commit `.env`.

---

## 1. MongoDB Atlas

1. Create a free M0 cluster (or use an existing one) in the region closest to your Render service.
2. **Database Access → Add New Database User**:
   - Authentication: password.
   - Role: `readWrite` on the `smartlib` database only (least privilege).
   - Use a long random password; store it in a password manager.
3. **Network Access → Add IP Address**:
   - Render's free instances do not provide a static outbound IP. Add `0.0.0.0/0`
     for the demo, or a narrower range if your plan/egress proxy provides one.
   - Atlas `mongodb+srv://…` connection strings are required for this project.
4. Copy the connection string:
   ```
   mongodb+srv://USER:PASSWORD@CLUSTER.mongodb.net/?retryWrites=true&w=majority
   ```
   The database name is selected by `SPRING_DATA_MONGODB_DATABASE=smartlib` (default `smartlib`).
5. If you previously deployed Iteration 3 into the same database, use a **new database name** or drop the
   old collections. The seeder only inserts when collections are empty.

---

## 2. Backend on Render

### Option A — Docker (recommended)

1. Push the repository to GitHub.
2. Render → **New → Web Service** → connect the repo.
3. Set:
   - **Root Directory**: `backend` (the root is `smartlib/`)
   - **Environment**: Docker
   - **Dockerfile Path**: `./Dockerfile`
   - **Health Check Path**: `/actuator/health`
4. Add environment variables:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `MONGODB_URI` | your Atlas URI (secret) |
| `JWT_SECRET` | ≥32 random characters (secret) |
| `APP_CORS_ALLOWED_ORIGIN` | `https://YOUR-APP.vercel.app` |
| `APP_SEED_DEMO` | `true` **for the first boot only**, then `false` |
| `APP_RATE_LIMIT_ENABLED` | `true` (optional; default true) |

Do **not** set `PORT`; Render injects it and the app uses `${PORT:8080}`.
5. Deploy. Wait for `/actuator/health` to return `{"status":"UP"}`.
6. Confirm the seed: log in as a demo librarian and call `GET /api/shelves` (12 shelves) and
   `GET /api/books?size=1` (`totalItems: 30`).
7. Change `APP_SEED_DEMO` to `false` and redeploy. The prod profile also defaults it to false; the
   explicit variable makes the intent clear. Existing data is never overwritten because seeding only
   runs when collections are empty.

### Option B — native Java runtime

- **Build Command**: `mvn clean package -DskipTests`
- **Start Command**: `java -jar target/smartlib-backend-0.0.1-SNAPSHOT.jar`
- **Root Directory**: `backend`
- Java 21 runtime; same env vars and health path.

### Free-tier cold start

Render free web services spin down after inactivity. The first request can take ~30–60 seconds.
The React UI shows a friendly error/retry state; the startup `ApplicationReadyEvent` hook catches up
expired pickups/overdue loans as soon as the process is awake. If the health check times out on the
very first request, retry once.

---

## 3. Frontend on Vercel

1. Vercel → **Add New → Project** → import the same repository.
2. Set:
   - **Root Directory**: `frontend`
   - **Framework Preset**: Vite
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
3. Environment variable (Production and Preview):
   ```
   VITE_API_URL=https://YOUR-BACKEND.onrender.com/api
   ```
   Include `/api`; do not add a trailing slash.
4. `frontend/vercel.json` already contains the SPA rewrite so `/my-loans`, `/map?shelf=A2`, etc. work on refresh.
5. Deploy. Copy the final Vercel URL into Render's `APP_CORS_ALLOWED_ORIGIN` (no trailing slash) and
   redeploy the backend.

If you use a custom domain, add that exact origin as a second comma-separated value:
```
https://smartlib.example.com,https://smartlib.vercel.app
```
Vercel preview deployments get random hostnames; either add the specific preview origin temporarily or
test against the production deployment.

---

## 4. CORS checklist

- Browser origin must exactly match an entry: scheme + host + optional port, **no trailing slash**.
- Comma-separate multiple origins; do not use `*` with credentials.
- After changing `APP_CORS_ALLOWED_ORIGIN`, restart/redeploy the backend.
- `OPTIONS` preflight is allowed for GET/POST/PUT/PATCH/DELETE with `Authorization` and `Content-Type`.
- `Content-Disposition` is exposed so CSV downloads keep their filenames.

---

## 5. One-time production seed

The seed is idempotent (it checks collection counts). The recommended sequence:

1. First backend deploy with `SPRING_PROFILES_ACTIVE=prod`, `APP_SEED_DEMO=true`.
2. Wait for startup; check the Render logs for `Seed check complete` and `Demo accounts —`.
3. Verify `GET /api/shelves` returns 12 and the catalogue returns 60 books.
4. Set `APP_SEED_DEMO=false`, redeploy. Demo data remains; future restarts do not add data.
5. Demo accounts (password `Password123`):
   - Students: `aarav@`, `diya@`, `kabir@`, `isha@`, `neha@`, `rohan@` `smartlib.test`
   - Librarians: `meera@`, `ravi@` `smartlib.test`
   - Change/remove demo credentials before using a real public deployment.

Never enable `APP_SEED_DEMO` on a production database that contains real data.

---

## 6. Post-deploy smoke test

Run these from your machine, replacing the URLs:

```bash
API=https://YOUR-BACKEND.onrender.com
WEB=https://YOUR-APP.vercel.app

# 1. Health (public; no token)
curl -fsS "$API/actuator/health"
# {"status":"UP",...}

# 2. Login and capture a token (do not paste the token into logs/screenshots)
TOKEN=$(curl -fsS -X POST "$API/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"meera@smartlib.test","password":"Password123"}' | jq -r .token)
test ${#TOKEN} -gt 100 && echo "login ok"

# 3. Catalogue page envelope
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$API/api/books?page=0&size=3&sort=title" | jq '{totalItems,totalPages,first:.items[0].title}'
# totalItems should be 30 on the seeded DB

# 4. Shelves and a shelf's books
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/api/shelves" | jq 'length'   # 12
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/api/shelves/A2/books" | jq 'length'

# 5. Reports + a CSV download
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/api/reports/circulation" | jq '{totalIssued,totalReturned}'
curl -fsS -D /tmp/csv.headers -o /tmp/overdue.csv \
  -H "Authorization: Bearer $TOKEN" "$API/api/reports/export?type=overdue"
head -1 /tmp/overdue.csv     # Student,Book,Due date,Days late,Accrued fine
grep -i content-type /tmp/csv.headers

# 6. Invariants (dev profile only; should not be reachable in prod)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  "$API/api/dev/invariants"   # expect 404/403 in prod
```

Browser checklist:
- [ ] Landing page loads over HTTPS; Login/Register work.
- [ ] Refresh `/my-loans` directly — Vercel rewrite serves the app (no 404).
- [ ] Student login → catalogue → "Locate on map" → `/map?shelf=…` highlights the shelf.
- [ ] Dark/light toggle persists after refresh; layout works at 360/768/1280 px.
- [ ] Librarian reports show charts/tables and CSV downloads open.
- [ ] DevTools is absent in production (no `VITE_DEV_TOOLS`); `/api/dev/*` is not reachable.
- [ ] Log in as a student with pending fines (Isha/Rohan on seed data) and verify requests are blocked.
- [ ] Check the browser console: no CORS errors and no failed asset requests.

---

## 7. Local Docker (if Docker is installed)

```bash
cd smartlib
cp .env.example .env
# edit MONGODB_URI, JWT_SECRET, and optionally VITE_API_URL
docker compose up --build
```

- Backend: `http://localhost:8080` (health at `/actuator/health`)
- Frontend: `http://localhost:3000`
- Atlas is used for data; no local Mongo container is started.
- The frontend image bakes `VITE_API_URL` at build time. Rebuild after changing it:
  `docker compose build --no-cache frontend`.

> **Verification status:** Docker was not installed on the development machine, so the image and
> compose flow are supplied but **NOT VERIFIED** locally. Render's Docker build follows the same
> `backend/Dockerfile`; Vercel uses the static Vite build.

---

## 8. Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Backend exits on startup: `JWT_SECRET is too short` | Set a ≥32-character secret |
| Backend exits: missing `MONGODB_URI` | Add the Atlas env var |
| Browser CORS error | Origin mismatch; no trailing slash; redeploy backend after changing config |
| Login returns 429 | 10 attempts/minute/IP; wait for `Retry-After` or restart the process |
| 401 immediately after login | `VITE_API_URL` points at the wrong backend, or token was cleared |
| Reports empty | Date range has no activity; extend the range. Waitlist length is current, not period-bound |
| Deep link 404 on Vercel | Ensure `vercel.json` is in `frontend/` and Root Directory is `frontend` |
| Render cold start | Retry after ~30–60 s; health check wakes the service |
| Images/assets 404 in Docker | Rebuild frontend; confirm nginx `try_files` and `dist/` copy |
