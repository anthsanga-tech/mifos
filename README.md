# Frontend notes for the team

Angular 14-style layout matching **Mifos `web-app`**. Sample **Welcome** only.

## Structure

```text
frontend/src/app/
  app.module.ts
  app-routing.module.ts
  app.component.*
  home/                      ← like web-app/src/app/home
    home.module.ts
    home-routing.module.ts
    home.component.*
    home.service.ts
```

## Next steps

Add a `users/` feature module (list/create) that calls:

`GET/POST /mini-fineract-provider/api/v1/users`

## Run

```bash
npm install
npm start
```

UI: http://localhost:4201

## Maker-checker inbox API

The frontend expects the savings provider to enforce the approval threshold on its existing deposit, withdrawal, transfer, and interest-posting endpoints. Commands with an amount of 10,000 or more must remain pending until a different user approves them; rejection must not move funds. The browser does not enforce these financial rules.

The inbox integration uses:

- `GET /mini-fineract-provider/api/v1/makerchecker` with optional `status` and `accountId` query parameters, returning an array of request objects.
- `POST /mini-fineract-provider/api/v1/makerchecker/{id}/approve`.
- `POST /mini-fineract-provider/api/v1/makerchecker/{id}/reject` with `{ "reason": "..." }`.

Request objects contain `id`, `type`, `accountId`, `accountNo`, `amount`, `status`, `madeBy`, `madeOn`, and optional `reason`. The provider must authorize approvals/rejections, reject self-approval, and record transaction and audit data only after approval.

## Standing instruction run-due API

`POST /mini-fineract-provider/api/v1/savings-accounts/{accountId}/standing-instructions/run-due` must execute only `ACTIVE` instructions, at most once per business date. A `PAUSED` instruction must not create a transfer, maker-checker request, or run-history record. For transfers at or above the provider's threshold, create a `PENDING` `TRANSFER` maker-checker request and link it to the instruction run without moving funds until approval. Return a run summary; the account screen displays it or shows the paused-instruction response in a red warning box and refreshes account activity. Run-history sections load from the run-history endpoint and appear only when run data exists (or while loading/showing a load error).
