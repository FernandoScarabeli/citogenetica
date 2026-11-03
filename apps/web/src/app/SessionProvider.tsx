import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { authApi, type createAuthApi } from '../api/auth-api';
import { httpClient, type HttpClient } from '../api/http-client';
import { usuarioApi, type createUsuarioApi } from '../api/usuario-api';
import { notificacaoApi } from '../api/notificacao-api';
import { isUnauthorized, type NetworkError } from '../api/errors';
import type { UsuarioResponseDTO } from '../api/contracts/usuario';
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
  updateUser: (user: UsuarioResponseDTO) => void;
  clearSession: () => void;
}

interface SessionProviderProps {
  children: ReactNode;
  auth?: AuthApi;
  usuario?: ReturnType<typeof createUsuarioApi>;
  client?: HttpClient;
  notifications?: Pick<typeof notificacaoApi, 'startRealtime' | 'stopRealtime'>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children, auth = authApi, usuario = usuarioApi, client = httpClient, notifications = notificacaoApi }: SessionProviderProps) {
  const [user, setUser] = useState<UsuarioResponseDTO | null>(null);
  const [status, setStatus] = useState<SessionStatus>('loading');
  const [loadError, setLoadError] = useState<unknown>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const statusRef = useRef(status);
  const locationRef = useRef(location);
  const sessionGenerationRef = useRef(0);

  statusRef.current = status;
  locationRef.current = location;

  const retry = useCallback(async () => {
    const requestGeneration = ++sessionGenerationRef.current;
    setStatus('loading');
    setLoadError(null);
    try {
      const currentUser = await usuario.getMe();
      if (requestGeneration !== sessionGenerationRef.current) return;
      setUser(currentUser);
      setStatus('authenticated');
    } catch (error) {
      if (requestGeneration !== sessionGenerationRef.current) return;
      setUser(null);
      if (isUnauthorized(error)) {
        setStatus('unauthenticated');
        return;
      }
      setLoadError(error);
      setStatus('error');
    }
  }, [usuario]);

  useEffect(() => {
    void retry();
  }, [retry]);

  useEffect(() => client.onUnauthorized(() => {
    const currentLocation = locationRef.current;
    if (statusRef.current !== 'authenticated') return;

    sessionGenerationRef.current += 1;
    const publicConfirmation = currentLocation.pathname === '/confirmar-email'
      || currentLocation.pathname === '/confirmar-troca-email';
    setUser(null);
    setLoadError(null);
    setStatus('unauthenticated');
    if (currentLocation.pathname !== '/entrar' && !publicConfirmation) {
      const returnTo = currentInternalPath(
        currentLocation.pathname,
        currentLocation.search,
        currentLocation.hash,
      );
      navigate('/entrar', { replace: true, state: { returnTo } });
    }
    notifications.stopRealtime();
  }), [client, navigate, notifications]);

  useEffect(() => {
    if (status !== 'authenticated' || user === null) return;
    return notifications.startRealtime(user.id);
  }, [notifications, status, user?.id]);

  const login = useCallback(async (email: string, senha: string) => {
    const requestGeneration = ++sessionGenerationRef.current;
    await auth.login({ email: email.trim(), senha });
    const currentUser = await usuario.getMe();
    if (requestGeneration === sessionGenerationRef.current) {
      setUser(currentUser);
      setLoadError(null);
      setStatus('authenticated');
    }
    return currentUser;
  }, [auth, usuario]);

  const updateUser = useCallback((currentUser: UsuarioResponseDTO) => {
    sessionGenerationRef.current += 1;
    setUser(currentUser);
    setLoadError(null);
    setStatus('authenticated');
  }, []);

  const clearSession = useCallback(() => {
    notifications.stopRealtime();
    sessionGenerationRef.current += 1;
    setUser(null);
    setLoadError(null);
    setStatus('unauthenticated');
  }, [notifications]);

  const logout = useCallback(async () => {
    notifications.stopRealtime();
    const requestGeneration = ++sessionGenerationRef.current;
    let notice: string | undefined;
    try {
      await auth.logout();
    } catch {
      notice = 'Não foi possível confirmar o encerramento da sessão. Tente novamente.';
    } finally {
      if (requestGeneration === sessionGenerationRef.current) {
        setUser(null);
        setLoadError(null);
        setStatus('unauthenticated');
        navigate('/entrar', { replace: true, state: notice ? { notice } : null });
      }
    }
  }, [auth, navigate, notifications]);

  return <SessionContext.Provider value={{ user, status, loadError, retry, login, logout, updateUser, clearSession }}>
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
