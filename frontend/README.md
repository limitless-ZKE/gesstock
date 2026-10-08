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

Copy `.env.example` to `.env.local` to configure the API URL. The TVA rate is not
configured in the frontend: the backend imposes it (`APP_TVA_TAUX`, `0` by default)
and the till reads it from `GET /api/caisse/parametres`.

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
