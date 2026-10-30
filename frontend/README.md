# Frontend

The frontend is an Angular 14 single-page application for the training backend. It includes login and home pages, user and client management, savings product/account screens, transaction activity, and a maker-checker inbox.

## Run locally

From this directory:

```powershell
npm install
npm start
```

Open http://localhost:4201. The backend must be running at `http://localhost:8088` for API requests to work. The Angular development server forwards `/mini-fineract-provider` requests using [proxy.conf.json](./proxy.conf.json).

Create a production build with:

```powershell
npm run build
```

## Authentication

The default frontend environment uses HTTP Basic authentication (`oauth.enabled` is `false`). The optional OAuth2/JWT mode uses the Keycloak settings in [src/environments/environment.ts](./src/environments/environment.ts) and must match the backend security configuration.

The browser provides the management interface; authorization and financial rules, including maker-checker approvals and standing-instruction processing, are enforced by the backend.
