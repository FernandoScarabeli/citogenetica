import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

interface InvitationTokenContextValue {
  token: string | null;
  setToken: (token: string) => void;
  clearToken: () => void;
}

const InvitationTokenContext = createContext<InvitationTokenContextValue | null>(null);

export function InvitationTokenProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const clearToken = useCallback(() => setToken(null), []);
  const value = useMemo(() => ({ token, setToken, clearToken }), [token, clearToken]);
  return <InvitationTokenContext.Provider value={value}>
    {children}
  </InvitationTokenContext.Provider>;
}

export function useInvitationToken(): InvitationTokenContextValue {
  const context = useContext(InvitationTokenContext);
  if (!context) throw new Error('useInvitationToken deve ser usado dentro de InvitationTokenProvider.');
  return context;
}
