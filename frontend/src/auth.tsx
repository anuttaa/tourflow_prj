import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { login as apiLogin, logout as apiLogout, refreshSession, SessionUser, setSessionListener } from './api';

interface AuthState {
  user: SessionUser | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  has: (...roles: string[]) => boolean;
}

const Ctx = createContext<AuthState>(null as unknown as AuthState);
export const useAuth = () => useContext(Ctx);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setSessionListener((s) => setUser(s ? s.user : null));
    refreshSession().finally(() => setReady(true));
  }, []);

  const value: AuthState = {
    user, ready,
    login: async (e, p) => { await apiLogin(e, p); },
    logout: async () => { await apiLogout(); },
    has: (...roles) => !!user && roles.some((r) => user.roles.includes(r)),
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
