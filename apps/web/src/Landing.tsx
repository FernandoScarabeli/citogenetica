import { ArrowRight, Microscope } from 'lucide-react';
import { useSize } from '@/lib/size-context';
import { Link, Navigate } from 'react-router';
import { useSession } from './app/SessionProvider';
import './landing.css';

export function Landing() {
  const { type } = useSize();
  const { status } = useSession();
  if (status === 'authenticated') return <Navigate to="/laboratorios" replace/>;
  return <main className="access-page">
    <section className="access-main" aria-labelledby="access-title">
      <div className="access-brand"><span className="access-brand-mark"><Microscope size={22}/></span><span>citogenética<small>Laboratório de Citogenética · UFLA</small></span></div>
      <div className="access-content">
        <div className="access-intro"><h1 id="access-title" className={type.display}>Registro de lâminas de citogenética</h1><p className={type.body}>Conte as células ao microscópio, guarde cada contagem e compartilhe com o seu laboratório.</p></div>
        <div className="access-form">
          <Link className="access-signin" to="/entrar">Entrar <ArrowRight size={17} aria-hidden="true"/></Link>
          <Link className="auth-secondary-link landing-create-account" to="/criar-conta">Criar conta</Link>
        </div>
      </div>
      <p className="access-footer">Laboratório de Citogenética<br/>Universidade Federal de Lavras</p>
    </section>
    <aside className="access-visual" aria-label="Imagem de microscopia"><img src="/images/mitose-raiz-cebola.jpg" alt="Células da ponta de raiz de cebola observadas ao microscópio"/><div className="access-visual-caption"><div><span>Um olhar mais próximo.</span><p>Ciência começa<br/>com observação.</p></div><small>Raiz de cebola · microscopia de contraste de fase<br/><a href="https://commons.wikimedia.org/wiki/File:Onion_Root_Tip_Cells_Mitosis.jpg" target="_blank" rel="noreferrer">Catfaster / Wikimedia Commons · CC BY-SA 4.0</a></small></div></aside>
  </main>;
}
