import axios from "axios";
import { API_BASE_URL, api } from "../services/api";

export interface CurrentUser {
  username: string;
  roles: string[];
}

const backendBaseUrl = API_BASE_URL.replace(/\/api\/?$/, "");

export async function signIn(username: string, password: string) {
  const form = new URLSearchParams({ username, password });
  await axios.post(`${backendBaseUrl}/login`, form, {
    withCredentials: true,
    withXSRFToken: true,
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  return getCurrentUser();
}

export async function getCurrentUser() {
  const response = await api.get<CurrentUser>("/auth/me");
  return response.data;
}

export async function signOut() {
  await axios.post(
    `${backendBaseUrl}/logout`,
    new URLSearchParams(),
    {
      withCredentials: true,
      withXSRFToken: true,
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    },
  );
}
