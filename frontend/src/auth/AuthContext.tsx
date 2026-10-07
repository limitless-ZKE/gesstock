import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  getCurrentUser,
  signIn,
  signOut,
  type CurrentUser,
} from "./authService";

interface AuthContextValue {
  user: CurrentUser | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    getCurrentUser()
      .then((currentUser) => {
        if (mounted) setUser(currentUser);
      })
      .catch(() => {
        if (mounted) setUser(null);
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    const onUnauthorized = () => setUser(null);
    window.addEventListener("gesstock:unauthorized", onUnauthorized);
    return () => {
      mounted = false;
      window.removeEventListener("gesstock:unauthorized", onUnauthorized);
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const currentUser = await signIn(username, password);
    setUser(currentUser);
  }, []);

  const logout = useCallback(async () => {
    try {
      await signOut();
    } finally {
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, logout }),
    [user, loading, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth doit être utilisé dans AuthProvider.");
  return context;
}
