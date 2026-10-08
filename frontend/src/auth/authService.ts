import { api } from "../services/api";

export interface CurrentUser {
  username: string;
  roles: string[];
}

/** Connexion : 204 si réussie, 401 « problem+json » (code IDENTIFIANTS_INVALIDES) sinon ; plus aucune redirection HTML. */
export async function signIn(username: string, password: string) {
  await api.post("/auth/login", new URLSearchParams({ username, password }));
  return getCurrentUser();
}

export async function getCurrentUser() {
  const response = await api.get<CurrentUser>("/auth/me");
  return response.data;
}

export async function signOut() {
  await api.post("/auth/logout");
}
