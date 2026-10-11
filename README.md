# Public Fund System

A website that helps manage public funding, track project progress, review supporting documents, and keep records of payments and unused funds.

## What the system does

- Service Providers register, submit funding applications, manage project phases, upload evidence, and view payment records.
- Government Officers review evidence and explain decisions.
- Administrators review applications and phases, record payments, manage staff and users, and oversee project completion and unused funds.
- Notifications and audit records help users understand decisions and outstanding actions.

The application records payments and return receipts. It does not move money through a bank or automatically verify bank transactions.

## Technology

- Frontend: React and Vite
- Backend: Java 22 and Spring Boot
- Database: MySQL

## Local setup

### 1. Prerequisites

Install Java 22, a current Node.js version supported by the Vite version in `frontend/package.json`, and MySQL. The backend includes the Maven wrapper.

Use a dedicated development database. Do not load real user data into a shared demonstration environment.

### 2. Configure the backend privately

Create the database named `public_fund_db` in MySQL, or configure a different database URL.

Copy `backend/config/local.properties.example` to `backend/config/local.properties`. Supply your own database password and a unique random `PUBLIC_FUND_JWT_SECRET` of at least 32 bytes. Alternatively, provide the corresponding environment variables.

The real configuration file is ignored by Git. Never commit credentials. Spring Boot does not automatically load a `.env` file.

For the first Administrator, configure `PUBLIC_FUND_ADMIN_NAME`, `PUBLIC_FUND_ADMIN_EMAIL`, and `PUBLIC_FUND_ADMIN_PASSWORD` together. After the account is created, remove these bootstrap values. Existing accounts are not overwritten. See [staff account setup](backend/STAFF-ACCOUNTS.md).

### 3. Start the backend

From the `backend` folder in PowerShell:

```powershell
.\Start-Backend.ps1
```

Alternatively, with Java 22 configured:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend listens on port 8080. Local startup currently uses Hibernate schema updates. Existing databases must be backed up and their applicable migrations reviewed before upgrades; do not blindly rerun every SQL file.

### 4. Start the frontend

In a separate terminal, from the `frontend` folder:

```powershell
npm ci
npm run dev
```

Open the URL printed by Vite, normally `http://localhost:5173`.

### 5. Optional integrations

- Email verification and password recovery need working mail-server settings.
- Google sign-in needs an OAuth web client configured for the website's origin.
- Fund returns require official funder return instructions.
- For Gmail on Windows, `.\Start-Backend.ps1 -ConfigureMail` accepts an app password privately and stores it encrypted for that Windows user.

See [Google and email setup](backend/GOOGLE-AND-EMAIL-SETUP.md), [User Management](backend/USER-MANAGEMENT.md), and [fund reconciliation](backend/database/UNSPENT_FUNDS.md).

## Checks

From `frontend`:

```powershell
npm run build
node scripts/project-flows-test.mjs
node scripts/api-version-test.mjs
```

From `backend`, with private database configuration available:

```powershell
.\mvnw.cmd test
```

The normal context test connects to the configured development database. Destructive workflow integration tests are opt-in and use a separate `public_fund_workflow_test` database. Read [workflow safety notes](backend/WORKFLOW-SAFETY.md) before enabling them. Never point test configuration at a production database.

## What is deliberately not in this repository

Passwords and signing keys, local integration configuration, uploaded evidence and payment documents, database backups, logs, dependency folders, and generated builds.

## GitHub is not live hosting

Uploading this repository does not deploy the application. A live deployment also requires backend hosting, MySQL, persistent private file storage, HTTPS, the correct frontend API URL, allowed frontend origins, and deployment-specific Google/email settings.

Before public deployment, review security and dependencies, replace development settings, configure database migrations and backups, and use new production secrets. The Spring Boot version in this project is currently a snapshot, not a pinned stable release.
