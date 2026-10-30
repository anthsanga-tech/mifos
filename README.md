# Mifos Full-Stack Training Project

A training application with an Angular web interface and a Spring Boot/Jersey backend. The backend persists users, clients, and savings data in PostgreSQL and exposes a versioned REST API.

## Components

| Component | Technology | Local address |
|---|---|---|
| Frontend | Angular 14, TypeScript | http://localhost:4201 |
| Backend API | Spring Boot 3.5, Jersey, Java 25 toolchain | http://localhost:8088/mini-fineract-provider/api |
| Database | PostgreSQL 16 | `localhost:5433`, database `mini_fineract` |
| Optional identity provider | Keycloak 26 | http://localhost:9000 |

The frontend is configured to proxy `/mini-fineract-provider` requests to the local backend. Start the frontend and backend separately.

## Features

- Login with HTTP Basic authentication by default; optional OAuth2/JWT integration with Keycloak.
- Manage users and clients.
- Create and manage savings products and accounts, including product closure fees.
- Perform deposits, withdrawals, transfers, and interest posting; inspect transaction history and account audit trails.
- Manage account holds, notes, and standing instructions.
- Review, approve, reject, or cancel eligible maker-checker requests.
- Preview savings-account closure settlement and close accounts with eligible balance settlement.

## Requirements

- Docker with Docker Compose.
- Node.js and npm compatible with Angular 14 (Node.js 16 LTS is recommended).
- Java 21 to run the Gradle 8.12.1 wrapper. The Gradle build requests a Java 25 toolchain; install/configure that toolchain for compilation and tests.

## Run locally

### 1. Start PostgreSQL

From the backend directory, start the database service:

```powershell
cd backend
docker compose up -d postgresql
```

The checked-in local configuration connects to `localhost:5433` and database `mini_fineract`. Liquibase applies the schema changes when the backend starts.

To also start the optional Keycloak service, run `docker compose up -d` instead. Keycloak is not required for the default HTTP Basic authentication mode.

### 2. Start the backend

In the `backend` directory:

```powershell
.\gradlew.bat bootRun
```

The API base URL is `http://localhost:8088/mini-fineract-provider/api`. Check `GET /v1/health` to verify the service is responding.

Run backend tests with:

```powershell
.\gradlew.bat test
```

### 3. Start the frontend

In a second terminal, from the repository root:

```powershell
cd frontend
npm install
npm start
```

Open http://localhost:4201. The development server uses [frontend/proxy.conf.json](./frontend/proxy.conf.json) to forward API requests to `http://localhost:8088`.

Build the frontend with:

```powershell
npm run build
```

## API overview

All API routes use the `/mini-fineract-provider/api` base path.

| Resource | Route |
|---|---|
| Health | `GET /v1/health` |
| Authentication | `POST /v1/authentication` |
| Users | `/v1/users` |
| Clients | `/v1/clients` |
| Savings products | `/v1/savings-products` |
| Savings accounts and account operations | `/v1/savings-accounts` |
| Savings transactions and transfers | `/v1/savings-transactions` |
| Maker-checker requests | `/v1/savings-accounts-check-maker` |

Most resource routes support retrieval and create/update operations. The savings API also includes account holds, notes, audit trails, standing instructions, interest preview/posting, and account closure-settlement operations. The API resources in `backend/src/main/java/org/openmf/training/api` are the source of truth for supported methods and request shapes.

## Authentication and configuration

The default configuration enables HTTP Basic authentication and disables OAuth2. The frontend’s Basic-auth flow calls `/v1/authentication`; it does not define a universal default application login. Use credentials configured for your local application.

OAuth2/JWT is optional. The compose file starts Keycloak and imports the training realm. To switch modes, coordinate the backend flags in `backend/src/main/resources/application.properties` with `oauth.enabled` in `frontend/src/environments/environment.ts`; the backend and frontend must use the same authentication mode.

The maker-checker amount threshold defaults to `10000` in the backend application configuration. Financial rules and approval checks are enforced by the backend, not by the browser.

## Repository layout

```text
backend/   Spring Boot API, Liquibase migrations, and Docker Compose services
frontend/  Angular application and development proxy configuration
```

For backend-specific behavior and operational notes, see [backend/README.md](./backend/README.md). For frontend structure and UI notes, see [frontend/README.md](./frontend/README.md).
