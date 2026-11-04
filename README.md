# SplitSmart

**CSCI 310 · Team 01**  
Android expense-sharing application for USC students and alumni.

SplitSmart is designed to let registered users create groups, record and split expenses, view pairwise balances, record settlements made outside the app, and search transaction history and monthly summaries. Payments are **not** processed in the app.

Amounts currently use USD and each expense records one payer. Mixed-currency balance calculation and splitting one expense across multiple payers are not supported in this version.

## Project status

The Android app and Docker-based Spring Boot + MySQL development environment are implemented. Start the services using the Quick start instructions and check `/api/health` and `/api/health/db` before opening the app.

- **Accounts & Groups (Section 1):** registration, login/logout, profile editing, group creation, member listing, owner-only member addition/removal, member leave, group rename, and group deletion are implemented in the backend and Android app. The login screen also offers a recent-account picker for quick switching; it stores email addresses only, never passwords.
- **Expense Management (Section 2):** expense create/view/edit/delete, custom category labels, and optional receipt photo attachments are implemented in the backend and Android app. See [Expense API](#expense-api).
- **Expense Splitting (Section 3):** equal, exact-amount, and percentage splits, saved split inputs, and share previews are implemented in the backend and Android app. See [Expense splitting](#expense-splitting).
- **Balances & Settlements (Section 4):** pairwise balances, owed/receivable totals, suggested payments, and settlement create/edit/delete are implemented in the backend and Android app.
- **History & Monthly Summary (Section 5):** a searchable, filterable history of expenses and settlements and a monthly spending summary are implemented in the backend and Android app. See [History API](#history-api).

## Architecture

```text
Android app (Java, Android Studio, Retrofit)
       |
       | REST/JSON (Retrofit)
       v
Spring Boot backend (Java 17, Maven)
       |
       | JDBC / JPA
       v
MySQL 8.4 (Docker volume)
```

The Android app talks to the **Spring Boot API**, never directly to MySQL.

## Repository layout

```text
Group1/
├── SplitSmart/                 # Android Studio application
├── backend/                    # Spring Boot / Maven application
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── compose.yaml               # Local backend + MySQL services
├── .env.example               # Environment variable template
├── .gitignore
└── README.md
```

## Prerequisites

- Git
- Docker Desktop with a running Linux container engine (Docker Compose v2)
- Android Studio, Android SDK, and an Android emulator (for the Android app)
- Java 17 if running Spring Boot **outside** Docker; Docker builds include Java

The team has used Docker 24.0.2 and Compose 2.18.1 on Windows/WSL2. Newer compatible versions should generally work, but test them locally.

## Quick start: backend + database

Run these commands **from the repository root** (`Group1/`), not from `SplitSmart/` or `backend/`.

### 1. Get the code

```bash
git clone https://github.com/aiyiwuzh/Group1.git
cd Group1
```

If you already cloned it:

```bash
git pull origin main
```

### 2. Set environment variables

```bash
cp .env.example .env
```

Edit `.env` and replace placeholder values with your **own local development passwords**. The Compose configuration expects:

```dotenv
DB_PASSWORD=replace_with_a_strong_local_password
DB_ROOT_PASSWORD=replace_with_a_different_strong_local_password
JWT_SECRET=output_of_openssl_rand_base64_32
```

`JWT_SECRET` signs login tokens and must be a Base64 value of at least 32 bytes. Generate one with `openssl rand -base64 32`.

**Never commit `.env` or paste its real credentials into issues, screenshots, or chat.** `.env.example` must contain placeholders only. Docker Compose uses `.env` for variable substitution; it is not a substitute for a secure production secrets manager.

### 3. Start the services

```bash
docker compose up --build -d
```

The first run downloads the MySQL image and builds the backend, so it may take several minutes.

### 4. Check container health

```bash
docker compose ps
```

Expected: `db` is **healthy** and `backend` is **Up**. In the current configuration, the backend is published only to `127.0.0.1:8080` on the host; MySQL port 3306 is available to other containers on the Compose network, not published to the host.

### 5. Test the backend and database

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health/db
```

Expected responses with the current development code:

```text
SplitSmart backend is running!
MySQL connection successful!
```

These checks verify the local HTTP endpoint and a simple database query. They **do not** verify registration, persistence of application records, or the Android connection.

## Android Studio setup

1. Open Android Studio and choose **Open**.
2. Select `Group1/SplitSmart` (the folder containing the Android Gradle project).
3. Allow Gradle to sync; install any SDK components requested by Android Studio.
4. Start an Android Virtual Device (AVD) and run the app.

For future Retrofit integration:

| Android runtime | Backend base URL |
|---|---|
| Android emulator on the same development computer | `http://10.0.2.2:8080/` |
| Backend requests from the host computer | `http://localhost:8080/` |
| Physical Android device | Requires a reachable host address and appropriate network/port configuration; the current localhost-only port binding is **not** reachable from other devices. |

`10.0.2.2` is the Android emulator's special address for the host computer. It is **not** the address to use inside the backend container. Android may require a debug-only cleartext HTTP allowance for local development; use HTTPS for a publicly deployed API. The Retrofit client and full end-to-end Android connectivity are not yet confirmed.

## Common commands

Run from `Group1/`:

```bash
# Start/rebuild in the background
docker compose up --build -d

# Show status
docker compose ps

# Follow backend logs
docker compose logs -f backend

# Inspect database logs
docker compose logs --tail=100 db

# Restart only the backend
docker compose restart backend

# Stop containers (keep database volume)
docker compose down

# Start again
docker compose up -d
```

### Database persistence and reset

MySQL stores its data in the named Docker volume `mysql_data`. Normal container restarts and `docker compose down` preserve it.

**Destructive reset — removes local database data:**

```bash
docker compose down -v
```

Do **not** run that command if you need the data. Local Docker volumes are not stored in Git and are not automatically shared between teammates. Database schema migrations and optional seed data should be committed to the repository as they are developed.

Changing MySQL passwords in `.env` **after** the database volume has been initialized may not update existing MySQL accounts. Coordinate changes or intentionally reset the development database if it is safe to lose its contents.

## Backend development

Backend code lives in `backend/src/main/java/com/splitsmart/backend/`.

Current structure includes:

```text
backend/src/main/java/com/splitsmart/backend/
├── BackendApplication.java
├── config/SecurityConfig.java
└── controller/HealthController.java
```

The backend reads its JDBC URL, username, and password from environment variables supplied by Compose. The current JPA setting is `validate`: Hibernate checks mapped tables rather than creating them. When adding entities, introduce version-controlled migrations (for example, Flyway) and coordinate table names and relationships with the team.

The database health endpoint is intended for local development. Remove or restrict it before any public deployment; do not expose raw database details in public error responses.

### Tests

From the repository root, to run the backend tests with the Maven wrapper on Windows:

```bash
cd backend
./mvnw.cmd test
```

On macOS/Linux/Git Bash, if the wrapper is executable:

```bash
cd backend
./mvnw test
```

**Note:** Some Spring Boot integration tests may need a configured database or test profile. If a test fails, inspect its setup rather than assuming Docker is broken. The Dockerfile's current build skips running tests, so run tests separately before merging or submitting.

## Expense API

All expense endpoints require `Authorization: Bearer <token>` from `/api/auth/login`. Errors return `{"error": "message"}`.

| Method | Path | Who | Result |
|---|---|---|---|
| `POST` | `/api/groups/{groupId}/expenses` | Group members | `201` with the expense |
| `GET` | `/api/groups/{groupId}/expenses` | Group members | `200` list, newest first |
| `GET` | `/api/expenses/{id}` | Group members | `200` with the expense |
| `PUT` | `/api/expenses/{id}` | Creator only | `200` with the updated expense |
| `DELETE` | `/api/expenses/{id}` | Creator only | `204` |

Request body for create and edit:

```json
{
  "title": "Groceries",
  "amount": 42.50,
  "category": "FOOD",
  "payerId": 1,
  "participantIds": [1, 2, 3],
  "note": "Optional"
}
```

Rules enforced by the backend (`400` unless noted):

- `amount` is greater than 0 with at most 2 decimal places (stored as `DECIMAL(10,2)`).
- `category` is `FOOD`, `HOUSING`, `TRANSPORTATION`, `ENTERTAINMENT`, or `OTHER`; it defaults to `OTHER`.
- The payer and every participant must be group members; the payer must be a participant; at least 2 participants.
- The creator may record an expense someone else paid for. Payer, participants, and amount can be edited.
- `createdAt` is set by the server and never changes; there is no date field in the request.
- Only the creator can edit or delete (`403`). Non-members cannot view (`403`).
- `settled` is an informational label only. The creator can still edit or delete a labeled expense; balances and summaries recalculate from the resulting expense data. A settlement is a separate repayment record and does not set this label.

Responses include `payer`, `creator`, and `participants` as `{id, name, email}`, plus `createdAt`, `updatedAt`, `settled`, and `canEdit` (true when the current user may edit or delete).

Tables (migration `V2__create_expenses.sql`): `expenses` and `expense_participants`. Deleting a group deletes its expenses.

Tests:

```bash
# Unit tests (no database needed)
cd backend && ./mvnw test -Dtest='ExpenseServiceTest,ExpenseRequestValidationTest'

# End-to-end API tests (backend must be running)
./test_expenses.sh
```

On Android, a logged-in user taps **My Groups & Expenses**, picks a group, and opens `ExpenseListActivity`. The **View group members** screen lists members and gives owners controls to add members, rename the group, or delete it.

## Expense splitting

Every expense is divided between its participants with one split method. The create and edit request above accepts two optional fields; leaving them out gives an equal split.

```json
{
  "splitMethod": "EXACT",
  "splitValues": { "1": 20.00, "2": 15.25, "3": 10.25 }
}
```

| `splitMethod` | `splitValues` |
|---|---|
| `EQUAL` (default) | Not used. The amount is divided as evenly as possible. |
| `EXACT` | Dollars owed by each participant, keyed by user id. Must add up to `amount`. |
| `PERCENTAGE` | Percent for each participant, keyed by user id. Must add up to exactly 100; original values are retained when editing. |

To show the shares before saving, call the preview endpoint. It uses the same rules as create and **saves nothing**. Group members only.

```text
POST /api/groups/{groupId}/expenses/preview
{ "amount": 10.00, "payerId": 1, "participantIds": [1, 2, 3],
  "splitMethod": "EQUAL", "splitValues": null }
```

Both the preview and the expense responses return `splitMethod` and `shares`, one `{userId, name, amount, payer}` per participant. The payer is included in `shares`; their own share is not owed to anyone.

Rules enforced by the backend (`400`, with the reason in `{"error": ...}`):

- Every share is at least $0.00 with at most 2 decimal places, and **the shares always add up to `amount`**. Nothing is saved otherwise.
- `EXACT`: an amount for every participant (`0.00` is allowed). A blank, negative, or 3-decimal amount is rejected, and so is an amount for someone who is not in the expense.
- `PERCENTAGE`: a percentage for every participant (`0` is allowed). A blank, negative, or more-than-8-decimal percentage is rejected; the total must equal 100.
- The payer must be one of the participants, and there must be at least 2 participants.
- Rounding: shares are in whole cents. If the amount cannot be divided evenly (for example $10.00 three ways is $3.34, $3.33, $3.33), the leftover cents go to participants chosen pseudo-randomly. The choice depends only on the request, so the preview and the saved expense always match. Someone at 0% never receives a leftover cent.
- Editing an expense recalculates its shares.

Tables (migration `V3__create_expense_shares.sql`): `expenses.split_method` and `expense_shares` (one row per expense and participant). Migration `V5__settlement_notes_creators_and_split_values.sql` adds saved original percentage values so an expense can be reopened with its percentage split intact.

The Android expense form supports all three split methods, previews each participant's share, and preserves the selected method and values when editing.

Tests:

```bash
# Unit tests (no database needed)
cd backend && ./mvnw test -Dtest='SplitCalculatorTest,SplitPreviewRequestValidationTest,ExpenseServiceTest'
```

## Balances and settlements

`GET /api/groups/{groupId}/balances` returns the signed pairwise balances and separate amounts owed/receivable for the authenticated member. `GET /api/groups/{groupId}/balances/suggestions` provides optional suggested transfers; it does not combine or change the underlying pairwise debts.

Create a repayment made outside the app with `POST /api/groups/{groupId}/settlements`:

```json
{
  "payerId": 2,
  "payeeId": 1,
  "amount": 5.00,
  "note": "Cash repayment"
}
```

The authenticated user must be one of those two members (the debtor or recipient). Both must belong to the group, the amount must be positive, have at most two decimal places, and not exceed the current debt between them. The note is optional. Only the user who recorded a settlement can edit or delete it. Deleting a settlement recalculates the pairwise balance.

`settled` on an expense is an informational label and does not represent a payment. Marking an expense settled does not create a settlement or change balances. The creator can still edit or delete it; the balance is recalculated from the current expense data.

## History API

Both endpoints require `Authorization: Bearer <token>` and are for group members only (`403` otherwise, `404` if the group does not exist). Errors return `{"error": "message"}`. There is no history table: these read the same `expenses`, `expense_shares` and `settlements` rows the other features write, so edits appear right away and deleted items are gone.

### Transaction history

```text
GET /api/groups/{groupId}/history?q=&type=&category=&memberId=&sort=
```

Every parameter is optional:

| Parameter | Values | Effect |
|---|---|---|
| `q` | up to 100 characters | Case-sensitive search of expense titles and notes, settlement notes, and settlement payer/recipient names |
| `type` | `EXPENSE`, `SETTLEMENT`, `ALL` (default) | Only one kind of transaction |
| `category` | `FOOD`, `HOUSING`, `TRANSPORTATION`, `ENTERTAINMENT`, `OTHER` | Expenses in that category. Settlements have no category, so they are hidden. |
| `memberId` | a member's user id | Expenses the member paid for or took part in, and settlements they paid or received |
| `sort` | `NEWEST` (default), `OLDEST`, `AMOUNT_HIGH`, `AMOUNT_LOW` | Order of the list. Ties are ordered by type and id so the list never reshuffles. |

An unknown `type`, `category` or `sort`, or a `memberId` who is not in the group, returns `400`.

Response:

```json
{
  "items": [
    { "type": "SETTLEMENT", "id": 7, "title": "Bob paid Alice", "amount": 5.00,
      "category": null, "payer": {"id": 2, "name": "Bob", "email": "..."},
      "payee": {"id": 1, "name": "Alice", "email": "..."}, "participantCount": 2,
      "participants": null, "note": null,
      "createdAt": "2026-11-03T21:04:11.123456Z", "settled": false },
    { "type": "EXPENSE", "id": 12, "title": "Groceries", "amount": 30.00,
      "category": "FOOD", "payer": {"id": 1, "name": "Alice", "email": "..."},
      "payee": null, "participantCount": 2,
      "participants": [{"id": 1, "name": "Alice", "email": "..."}, {"id": 2, "name": "Bob", "email": "..."}], "note": null,
      "createdAt": "2026-11-03T20:58:40.654321Z", "settled": false }
  ],
  "totalCount": 4,
  "members": [{"id": 1, "name": "Alice", "email": "..."}, {"id": 2, "name": "Bob", "email": "..."}]
}
```

`totalCount` is the number of transactions in the group before filtering, so the app can tell "no transactions yet" from "nothing matches". `members` (sorted by name) fills the member filter.

### Monthly summary

```text
GET /api/groups/{groupId}/summary/monthly?month=2026-11&timeZone=America/Los_Angeles
```

- `month` is `YYYY-MM` and defaults to the current month.
- `timeZone` is an IANA id. It decides where the month starts and ends, and defaults to `America/Los_Angeles`. The Android app sends the phone's time zone.
- Expenses count toward the month of their `createdAt`. **Settlements are not counted**: they repay existing expenses rather than adding spending.

```json
{
  "month": "2026-11", "timeZone": "America/Los_Angeles",
  "expenseCount": 2, "totalSpending": 70.00, "yourShare": 15.00,
  "categories": [
    {"category": "ENTERTAINMENT", "expenseCount": 1, "total": 40.00},
    {"category": "FOOD", "expenseCount": 1, "total": 30.00}
  ]
}
```

`yourShare` adds up the current user's shares of those expenses. `categories` lists only categories with spending, largest first. All amounts are exact decimals.

### Android

On a group's expense list, tap **History · Monthly summary**:

- **History** shows expenses and settlements together, newest first, with their type, amount, involved members, and local `YYYY-MM-DD` date. Search is case-sensitive and includes settlement notes. Tapping an expense opens its detail screen; tapping a settlement shows its note and who paid whom.
- **Monthly summary** shows the month's group spending, number of expenses, your share, and a bar chart for spending by category. Use ‹ and › to change months; future months are disabled.
- Group owners can use the trash button to remove a member. Non-owner members can leave through the member list after settling any unpaid balance. The group owner can delete the group if it is no longer needed.
- Expense creators can attach one JPEG, PNG, or WebP receipt photo up to 5 MB, then open it from the expense detail screen. Custom category labels can be entered on the expense form and appear in history and category charts.

### Tests

```bash
# Unit tests (no database needed)
cd backend && ./mvnw test -Dtest='HistoryServiceTest'

# End-to-end API tests (backend must be running)
./test_history.sh

# Android unit tests (from SplitSmart/)
./gradlew :app:testDebugUnitTest --tests '*HistoryFiltersTest'
```

## Team workflow and commits

The team is implementing features sequentially on a shared repository. Each person should:

1. Pull the latest `main` before starting.
2. Confirm Docker services start and the health checks pass.
3. Implement a small, testable change; do not overwrite shared models or API contracts without coordinating.
4. Run relevant tests and check `git status`.
5. Commit with a descriptive message and push before handing off.
6. Tell the next teammate what changed, which commands were run, and any known issues.

Suggested feature order: (1) Accounts & Groups, (2) Expense Management, (3) Expense Splitting, (4) Balances & Settlements, (5) History & Summaries. All features share the same backend, database, and Android project.

Before pushing, verify that **no secrets, `.env`, generated build output, or local SDK paths** are staged:

```bash
git status
git diff --cached --name-only
```

## Demo and submission preparation

- Keep a stable commit **before the course submission deadline**. The TAs will inspect Git commit history and timestamps; code committed after the deadline will not count for grading.
- Submit the required source code and documentation to Brightspace by the deadline.
- The demo is a **15-minute live Zoom session**; no separate demo video is required under the announced policy.
- Test the exact version to be demonstrated, including Docker startup, Android emulator launch, and the key user workflows.
- A local Docker setup needs the demo computer running the containers; it is **not** a public deployment.
- Watch for the TA's official demo checklist, which may add specific scenarios.

## Troubleshooting

**`docker compose` cannot connect to the daemon**  
Start Docker Desktop and wait for its engine to become ready; then run `docker info`.

**Backend container exits or restarts**  
Run `docker compose logs --tail=100 backend`. Check Java compilation, Spring configuration, and database credentials.

**Database is not healthy**  
Run `docker compose logs --tail=100 db`. Check `.env`, available disk space, and whether a previously initialized volume uses different credentials.

**Port 8080 already in use**  
Find and stop the conflicting service, or deliberately change the published host port in `compose.yaml` and update the client base URL.

**`curl` returns 401/403**  
Check `SecurityConfig.java`: only explicitly permitted health endpoints should be unauthenticated. Other API routes require a valid JWT.

**Android emulator cannot reach backend**  
Verify the backend works from the host first, then use `10.0.2.2` rather than `localhost` in the emulator. Check Android Internet permission and debug cleartext HTTP configuration.

**App works on one computer but not another**  
Confirm the teammate copied `.env.example` to their own `.env`, started Compose locally, and applied the same migrations. Docker volumes and passwords are not transferred by Git.

---

**Repository:** https://github.com/aiyiwuzh/Group1  
**Course:** USC CSCI 310 · Team 01
