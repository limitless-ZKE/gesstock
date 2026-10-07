# GesStock 3.0 Frontend

React, TypeScript, Vite, Tailwind CSS and Lucide React client for the GesStock
Spring Boot API.

## Local development

Start the Spring Boot backend on `http://localhost:8080`, then from this
directory run:

```sh
npm install
npm run dev
```

Open `http://localhost:5173`. The backend must allow the Vite origin; the
development CORS configuration is set to `http://localhost:5173`.

Authentication uses the Spring Security session cookie. The API client sends
credentials with requests. Set `VITE_API_BASE_URL` to override the default
`http://localhost:8080/api` endpoint.

Copy `.env.example` to `.env.local` to configure the API URL and the business
TVA rate (`VITE_TVA_RATE`, percentage; defaults to `0`). The selected rate is
sent with direct sales and persisted by the backend. VAT rules/rates should be
configured for the business before production use.

## Production build

```sh
npm run build
npm run preview
```

The main routes are `/login`, `/dashboard`, `/caisse`, `/articles`,
`/approvisionnements`, `/clients-dettes` and `/statistiques`. The caisse route
includes cash-session controls, the POS catalogue/cart, payment validation and
receipt printing. The other module routes retain their initial navigation and
UI structure for later domain-specific implementation.
