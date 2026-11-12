import type { ReactNode, Ref } from 'react';
import { Microscope } from 'lucide-react';
import { useSize } from '../lib/size-context';

interface AuthLayoutProps {
  title: string;
  titleId: string;
  description: ReactNode;
  children: ReactNode;
  titleRef?: Ref<HTMLHeadingElement>;
  titleFocusable?: boolean;
}

export function AuthLayout({
  title,
  titleId,
  description,
  children,
  titleRef,
  titleFocusable = false,
}: AuthLayoutProps) {
  const { type } = useSize();

  return <main className="access-page auth-page">
    <section className="access-main" aria-labelledby={titleId}>
      <div className="access-brand">
        <span className="access-brand-mark"><Microscope size={22} aria-hidden="true"/></span>
        <span>citogenética<small>Laboratório de Citogenética · UFLA</small></span>
      </div>
      <div className="access-content">
        <div className="access-intro">
          <h1
            id={titleId}
            ref={titleRef}
            tabIndex={titleFocusable ? -1 : undefined}
            className={type.display}
          >{title}</h1>
          <p className={type.body}>{description}</p>
        </div>
        {children}
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
