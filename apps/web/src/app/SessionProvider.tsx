import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { authApi, type createAuthApi } from '../api/auth-api';
import { httpClient, type HttpClient } from '../api/http-client';
import { isUnauthorized, type NetworkError } from '../api/errors';
import type { UsuarioResponseDTO } from '../api/contracts/auth';
import { currentInternalPath } from './return-path';

type AuthApi = ReturnType<typeof createAuthApi>;
export type SessionStatus = 'loading' | 'authenticated' | 'unauthenticated' | 'error';

interface SessionContextValue {
  user: UsuarioResponseDTO | null;
  status: SessionStatus;
  loadError: unknown;
  retry: () => Promise<void>;
  login: (email: string, senha: string) => Promise<UsuarioResponseDTO>;
  logout: () => Promise<void>;
}

interface SessionProviderProps {
  children: ReactNode;
  auth?: AuthApi;
  client?: HttpClient;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children, auth = authApi, client = httpClient }: SessionProviderProps) {
  const [user, setUser] = useState<UsuarioResponseDTO | null>(null);
  const [status, setStatus] = useState<SessionStatus>('loading');
  const [loadError, setLoadError] = useState<unknown>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const statusRef = useRef(status);
  const locationRef = useRef(location);

  statusRef.current = status;
  locationRef.current = location;

  const retry = useCallback(async () => {
    setStatus('loading');
    setLoadError(null);
    try {
      const currentUser = await auth.getMe();
      setUser(currentUser);
      setStatus('authenticated');
    } catch (error) {
      setUser(null);
      if (isUnauthorized(error)) {
        setStatus('unauthenticated');
        return;
      }
      setLoadError(error);
      setStatus('error');
    }
  }, [auth]);

  useEffect(() => {
    void retry();
  }, [retry]);

  useEffect(() => client.onUnauthorized(() => {
    const currentLocation = locationRef.current;
    if (statusRef.current !== 'authenticated' || currentLocation.pathname === '/entrar') return;

    const returnTo = currentInternalPath(
      currentLocation.pathname,
      currentLocation.search,
      currentLocation.hash,
    );
    setUser(null);
    setLoadError(null);
    setStatus('unauthenticated');
    navigate('/entrar', { replace: true, state: { returnTo } });
  }), [client, navigate]);

  const login = useCallback(async (email: string, senha: string) => {
    await auth.login({ email: email.trim(), senha });
    const currentUser = await auth.getMe();
    setUser(currentUser);
    setLoadError(null);
    setStatus('authenticated');
    return currentUser;
  }, [auth]);

  const logout = useCallback(async () => {
    let notice: string | undefined;
    try {
      await auth.logout();
    } catch {
      notice = 'Não foi possível confirmar o encerramento da sessão. Tente novamente.';
    } finally {
      setUser(null);
      setLoadError(null);
      setStatus('unauthenticated');
      navigate('/entrar', { replace: true, state: notice ? { notice } : null });
    }
  }, [auth, navigate]);

  return <SessionContext.Provider value={{ user, status, loadError, retry, login, logout }}>
    {children}
  </SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession deve ser usado dentro de SessionProvider.');
  return session;
}

export function isSessionNetworkError(error: unknown): error is NetworkError {
  return error instanceof Error && error.name === 'NetworkError';
}
