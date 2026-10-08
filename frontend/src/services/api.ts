import axios, { AxiosError } from "axios";
import { parseApiError } from "./apiError";

/**
 * Adresse de l'API. Version construite (npm run build) : "/api", sur le MÊME serveur que l'interface — c'est ce qui permet de
 * l'ouvrir depuis n'importe quel poste du réseau, à l'adresse du serveur, sans réglage. Développement (npm run dev) : le
 * serveur Java tourne à part, sur le port 8080. VITE_API_BASE_URL impose une autre adresse dans les deux cas.
 */
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.PROD ? "/api" : "http://localhost:8080/api");

export const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  // Session par cookie + protection CSRF : axios relit le cookie XSRF-TOKEN
  // et renvoie sa valeur dans l'en-tête X-XSRF-TOKEN.
  withXSRFToken: true,
  headers: { Accept: "application/json" },
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const apiError = parseApiError(error);

    // Session expirée ou absente : AuthContext renvoie l'utilisateur vers la connexion.
    if (apiError.status === 401) {
      window.dispatchEvent(new CustomEvent("gesstock:unauthorized"));
    }
    // Erreurs que l'écran ne peut pas traiter lui-même : droits, serveur, réseau.
    if (apiError.status === 403 || apiError.code === "RESEAU" || (apiError.status ?? 0) >= 500) {
      const reference = apiError.requestId ? ` (réf. ${apiError.requestId.slice(0, 8)})` : "";
      window.dispatchEvent(new CustomEvent("gesstock:api-error", { detail: apiError.message + reference }));
    }

    return Promise.reject(error);
  },
);
