import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowRight, Microscope } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router';
import { isRateLimited, isUnauthorized, NetworkError } from '../api/errors';
import { useSize } from '../lib/size-context';
import { useSession } from './SessionProvider';
import { safeInternalPath } from './return-path';

interface LoginLocationState {
  returnTo?: unknown;
  notice?: unknown;
}

function loginNotice(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

export function LoginPage() {
  const { type } = useSize();
  const { login } = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [credentialsInvalid, setCredentialsInvalid] = useState(false);
  const [retrySeconds, setRetrySeconds] = useState<number | null>(null);
  const [retryReady, setRetryReady] = useState(false);
  const errorRef = useRef<HTMLDivElement>(null);
  const focusErrorRef = useRef(false);
  const locationState = location.state as LoginLocationState | null;
  const notice = loginNotice(locationState?.notice);

  useEffect(() => {
    if (message && focusErrorRef.current) {
      errorRef.current?.focus();
      focusErrorRef.current = false;
    }
  }, [message]);

  useEffect(() => {
    if (retrySeconds === null || retrySeconds <= 0) return;
    const timer = window.setInterval(() => {
      setRetrySeconds((remaining) => remaining === null ? null : Math.max(remaining - 1, 0));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [retrySeconds]);

  useEffect(() => {
    if (retrySeconds === 0) setRetryReady(true);
  }, [retrySeconds]);

  function showError(text: string) {
    focusErrorRef.current = true;
    setMessage(text);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || (retrySeconds !== null && retrySeconds > 0)) return;

    setSubmitting(true);
    setMessage(null);
    setCredentialsInvalid(false);
    setRetryReady(false);
    try {
      await login(email, senha);
      const returnTo = safeInternalPath(locationState?.returnTo);
      navigate(returnTo ?? '/app', { replace: true });
    } catch (error) {
      if (isUnauthorized(error)) {
        setCredentialsInvalid(true);
        showError('E-mail ou senha inválidos.');
      } else if (isRateLimited(error)) {
        const seconds = error.retryAfterSeconds;
        setRetrySeconds(seconds && seconds > 0 ? seconds : null);
        showError(seconds && seconds > 0 ? 'Muitas tentativas.' : 'Muitas tentativas. Tente novamente mais tarde.');
      } else if (error instanceof NetworkError) {
        showError('Não foi possível conectar à API. Verifique sua conexão e tente novamente.');
      } else {
        showError('Não foi possível entrar. Tente novamente.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  const descriptionId = message ? 'login-error' : undefined;
  const countdown = retrySeconds !== null && retrySeconds > 0
    ? `Aguarde ${retrySeconds} s`
    : null;
  const errorMessage = message === 'Muitas tentativas.' && retrySeconds !== null && retrySeconds > 0
    ? `Muitas tentativas. Tente novamente em ${retrySeconds} ${retrySeconds === 1 ? 'segundo' : 'segundos'}.`
    : message;

  return <main className="access-page login-page">
    <section className="access-main" aria-labelledby="login-title">
      <div className="access-brand">
        <span className="access-brand-mark"><Microscope size={22} aria-hidden="true"/></span>
        <span>citogenética<small>Laboratório de Citogenética · UFLA</small></span>
      </div>
      <div className="access-content">
        <div className="access-intro">
          <h1 id="login-title" className={type.display}>Acesse sua pesquisa.</h1>
          <p className={type.body}>Entre para continuar no seu espaço de pesquisa.</p>
        </div>
        {notice && <p className="login-notice" role="status">{notice}</p>}
        {errorMessage && <div ref={errorRef} id="login-error" className="login-error" role="alert" tabIndex={-1}>{errorMessage}</div>}
        {retryReady && <p className="login-notice" role="status">Você já pode tentar novamente.</p>}
        <form className="access-form" onSubmit={submit}>
          <div className="access-field">
            <label htmlFor="login-email">E-mail</label>
            <input
              id="login-email"
              name="email"
              type="email"
              autoComplete="username"
              required
              maxLength={254}
              value={email}
              aria-invalid={credentialsInvalid || undefined}
              aria-describedby={descriptionId}
              onChange={(event) => { setEmail(event.target.value); setMessage(null); setCredentialsInvalid(false); }}
            />
          </div>
          <div className="access-field">
            <label htmlFor="login-password">Senha</label>
            <input
              id="login-password"
              name="senha"
              type="password"
              autoComplete="current-password"
              required
              value={senha}
              aria-invalid={credentialsInvalid || undefined}
              aria-describedby={descriptionId}
              onChange={(event) => { setSenha(event.target.value); setMessage(null); setCredentialsInvalid(false); }}
            />
          </div>
          <button className="access-signin" type="submit" disabled={submitting || !!countdown}>
            {submitting ? 'Entrando…' : countdown ?? 'Entrar'}
            {!submitting && !countdown && <ArrowRight size={17} aria-hidden="true"/>}
          </button>
        </form>
      </div>
      <p className="access-footer">Laboratório de Citogenética<br/>Universidade Federal de Lavras</p>
    </section>
    <aside className="access-visual" aria-label="Imagem de microscopia">
      <img src="/images/mitose-raiz-cebola.jpg" alt="Células da ponta de raiz de cebola observadas ao microscópio"/>
      <div className="access-visual-caption">
        <div><span>Um olhar mais próximo.</span><p>Ciência começa<br/>com observação.</p></div>
        <small>Raiz de cebola · microscopia de contraste de fase<br/><a href="https://commons.wikimedia.org/wiki/File:Onion_Root_Tip_Cells_Mitosis.jpg" target="_blank" rel="noreferrer">Catfaster / Wikimedia Commons · CC BY-SA 4.0</a></small>
      </div>
    </aside>
  </main>;
}
