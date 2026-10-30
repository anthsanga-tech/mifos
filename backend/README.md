# SG-CBS Fineract (training mini backend)

Sample Spring Boot + JAX-RS users API (Java 25 toolchain).

## Run

```bat
docker compose up -d
set JAVA_HOME=C:\Program Files\Zulu\zulu-21
set GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT
run-gradle.cmd bootRun
```

- Health: http://localhost:8088/mini-fineract-provider/api/v1/health
- Users: http://localhost:8088/mini-fineract-provider/api/v1/users

DB: localhost:5433 / mini_fineract / postgres / postgres

## Savings product fees

Savings products include a configurable flat closure fee. It is stored as `closure_fee` and defaults to zero for existing products.

## Close and settle savings accounts

Use `GET /v1/savings-accounts/{accountId}/close-settlement-preview` to inspect blockers and eligible sibling destinations before calling `POST /v1/savings-accounts/{accountId}/close-settlement` with an optional `destinationAccountId`. The service requires an ACTIVE, unfrozen account, no active holds or pending maker-checker requests, and available funds sufficient for the closure fee. It transfers any residual balance to an unfrozen ACTIVE sibling account, charges the fee, closes the account, and cancels open standing instructions involving it. Settlement transfers at or above the maker-checker threshold remain pending; approval completes settlement and closes the account atomically, recording audit events in order: `CHECKER_APPROVE`, `CLOSURE_FEE`, `TRANSFER_OUT`, then `CLOSED`. Settlement transfers are recorded in both account transaction histories and audit trails using a `TRF-<UUID>` group ID. Transfers involving a closed account cannot be undone.

## Security (same idea as Apache Fineract / web-app)

| Mode | Default | How |
|------|---------|-----|
| **HTTP Basic** | **Yes** | Login `POST /v1/authentication`, then `Authorization: Basic …` |
| **OAuth2 / JWT** | Optional | Keycloak on :9000 + flags — see `c:\mifos\DOCS\04-oauth2-jwt-setup.md` |

```properties
training.security.basicauth.enabled=true
training.security.oauth2.enabled=false
```

Frontend: `environment.oauth.enabled = false` (Basic). Set `true` only when using Keycloak.

## Standing instruction run-due behavior

`POST /mini-fineract-provider/api/v1/savings-accounts/{accountId}/standing-instructions/run-due` processes due `ACTIVE` instructions at most once per business day. Transfers below the configured maker-checker threshold execute immediately. Transfers at or above the threshold create a `PENDING` `TRANSFER` request in `m_savings_maker_checker`, leave both account balances unchanged until approval, and create a `PENDING_APPROVAL` instruction-run record linked to the request. Its run reason and `SI_EXECUTE` audit event include the pending request ID. The standing instruction's next due date advances after queuing.

Approving a pending standing-instruction transfer changes its run to `SUCCESS` and stores the transfer group ID. Rejecting it changes the run to `REJECT` and stores the checker-provided rejection reason.

Due `PAUSED` instructions do not create transfer, maker-checker, or run-history rows and do not advance their next due date. The endpoint returns an explanatory HTTP 409 response; the account screen shows it in a red warning box.
