import '@fontsource-variable/inter/opsz.css';
import '../theme.css';
import '../styles.css';
import '../typography.css';
import './app.css';
import { useLayoutEffect, type ReactNode } from 'react';
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { SizeProvider } from '../lib/size-context';
import { Landing } from '../Landing';
import { Laboratories } from '../Laboratories';
import { Slides } from '../Slides';
import { LoginPage } from './LoginPage';
import { CreateAccountPage } from './CreateAccountPage';
import { ConfirmEmailPage } from './ConfirmEmailPage';
import { prepareInitialConfirmationToken } from './confirmation-token';
import { SessionProvider, isSessionNetworkError, useSession } from './SessionProvider';
import { currentInternalPath } from './return-path';

function PrepareConfirmationToken({ children }: { children: ReactNode }) {
  const location = useLocation();

  useLayoutEffect(() => {
    if (location.pathname === '/confirmar-email') prepareInitialConfirmationToken();
  }, [location.pathname, location.search, location.hash]);

  return children;
}

function ProtectedRoutes() {
  const { status, loadError, retry } = useSession();
  const location = useLocation();

  if (status === 'loading') {
    return <main className="session-state" aria-live="polite"><p>Verificando sua sessão…</p></main>;
  }

  if (status === 'error') {
    return <main className="session-state" aria-labelledby="session-error-title">
      <h1 id="session-error-title">Não foi possível verificar sua sessão</h1>
      <p>{isSessionNetworkError(loadError)
        ? 'Verifique sua conexão e tente novamente.'
        : 'O serviço está indisponível no momento. Tente novamente.'}</p>
      <button className="button primary" type="button" onClick={() => void retry()}>Tentar de novo</button>
    </main>;
  }

  if (status === 'unauthenticated') {
    const returnTo = currentInternalPath(location.pathname, location.search, location.hash);
    return <Navigate to="/entrar" replace state={{ returnTo }} />;
  }

  return <Outlet />;
}

function NotFoundPage() {
  return <main className="not-found" aria-labelledby="not-found-title">
    <p className="not-found-code">404</p>
    <h1 id="not-found-title">Página não encontrada</h1>
    <p>O endereço informado não corresponde a uma página disponível.</p>
    <Link className="button primary" to="/">Voltar à página inicial</Link>
  </main>;
}

function AuthenticatedApp() {
  const { user, logout } = useSession();
  if (!user) return null;
  return <Laboratories
    user={user}
    onLogout={logout}
    renderSlides={(laboratory, onBack) => <Slides key={laboratory?.id ?? 'avulsas'} laboratory={laboratory} onBack={onBack}/>}
  />;
}

function RouteTable() {
  return <Routes>
    <Route path="/" element={<Landing/>}/>
    <Route path="/entrar" element={<LoginPage/>}/>
    <Route path="/criar-conta" element={<CreateAccountPage/>}/>
    <Route path="/confirmar-email" element={<ConfirmEmailPage/>}/>
    <Route element={<ProtectedRoutes/>}>
      <Route path="/app" element={<AuthenticatedApp/>}/>
      <Route path="/laboratorios" element={<AuthenticatedApp/>}/>
      <Route path="/perfil" element={<AuthenticatedApp/>}/>
    </Route>
    <Route path="*" element={<NotFoundPage/>}/>
  </Routes>;
}

export function App() {
  return <SizeProvider>
    <BrowserRouter>
      <PrepareConfirmationToken>
        <SessionProvider>
          <RouteTable/>
        </SessionProvider>
      </PrepareConfirmationToken>
    </BrowserRouter>
  </SizeProvider>;
}
