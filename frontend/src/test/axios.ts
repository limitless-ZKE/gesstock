import { AxiosError, type AxiosResponse } from "axios";

/** Réponse HTTP minimale, comme en construit axios. */
export function reponse(status: number, data: unknown, headers: Record<string, string> = {}): AxiosResponse {
  return { status, data, headers, statusText: "", config: { headers: {} } } as unknown as AxiosResponse;
}

/** Erreur HTTP « problem+json » telle que la lève axios pour un statut d'erreur. */
export function erreurHttp(status: number, data: unknown = {}, headers: Record<string, string> = {}): AxiosError {
  return new AxiosError("Request failed", "ERR_BAD_REQUEST", undefined, undefined, reponse(status, data, headers));
}

/** Erreur sans réponse : serveur éteint, coupure réseau. */
export function erreurReseau(): AxiosError {
  return new AxiosError("Network Error", "ERR_NETWORK");
}
