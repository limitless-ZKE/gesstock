import axios, { AxiosError } from "axios";

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

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
  (error: AxiosError<{ message?: string }>) => {
    const status = error.response?.status;
    const message =
      error.response?.data?.message ??
      (status === 403
        ? "Vous n’avez pas les droits nécessaires pour cette action."
        : status === 500
          ? "Une erreur serveur est survenue. Réessayez plus tard."
          : error.message);

    if (status === 401) {
      window.dispatchEvent(new CustomEvent("gesstock:unauthorized"));
    }
    if (status === 403 || status === 500) {
      window.dispatchEvent(
        new CustomEvent("gesstock:api-error", { detail: message }),
      );
    }

    return Promise.reject(error);
  },
);
