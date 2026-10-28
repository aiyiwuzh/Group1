# SplitSmart

**CSCI 310 · Team 01**  
Android expense-sharing application for USC students and alumni.

SplitSmart is designed to let registered users create groups, record and split expenses, view pairwise balances, record settlements made outside the app, and search transaction history and monthly summaries. Payments are **not** processed in the app.

## Project status

The Android starter project and the Docker-based Spring Boot + MySQL development environment have been set up. The `/api/health` and `/api/health/db` endpoints have been tested locally. Authentication, groups, expenses, splitting, balances, settlements, and history are planned features; **do not assume they are implemented yet**.

## Architecture

```text
Android app (Java, Android Studio)
       |
       | REST/JSON (Retrofit planned)
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
```

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
Check `SecurityConfig.java`: only explicitly permitted health endpoints should be unauthenticated. Other routes require the planned authentication configuration.

**Android emulator cannot reach backend**  
Verify the backend works from the host first, then use `10.0.2.2` rather than `localhost` in the emulator. Check Android Internet permission and debug cleartext HTTP configuration.

**App works on one computer but not another**  
Confirm the teammate copied `.env.example` to their own `.env`, started Compose locally, and applied the same migrations. Docker volumes and passwords are not transferred by Git.

---

**Repository:** https://github.com/aiyiwuzh/Group1  
**Course:** USC CSCI 310 · Team 01
