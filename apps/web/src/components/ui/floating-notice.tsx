import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { CircleCheck } from 'lucide-react';
import { Alert, AlertIcon, AlertTitle } from './alert-1';

const EXIT_DURATION = 180;

export function FloatingNotice({ message, onClose, duration = 5000 }: { message: string; onClose: () => void; duration?: number }) {
  const [content, setContent] = useState(message);
  // Mantém o conteúdo montado até a transição de saída terminar. Uma nova
  // mensagem cancela a remoção e a transição retoma da posição atual.
  useEffect(() => {
    if (message) {
      setContent(message);
      return;
    }
    const timer = window.setTimeout(() => setContent(''), EXIT_DURATION);
    return () => window.clearTimeout(timer);
  }, [message]);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [hidden, setHidden] = useState(document.hidden);
  useEffect(() => {
    const update = () => setHidden(document.hidden);
    document.addEventListener('visibilitychange', update);
    return () => document.removeEventListener('visibilitychange', update);
  }, []);
  useEffect(() => {
    if (!message || hovered || focused || hidden) return;
    const timer = window.setTimeout(() => closeRef.current(), duration);
    return () => window.clearTimeout(timer);
  }, [message, duration, hovered, focused, hidden]);

  if (!content) return null;
  return <div className="floating-notice" data-state={message ? 'open' : 'closed'} inert={!message} aria-hidden={!message || undefined}
    style={{ '--notice-exit-duration': `${EXIT_DURATION}ms` } as CSSProperties} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}
    onFocusCapture={() => setFocused(true)} onBlurCapture={event => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocused(false); }}>
    <Alert variant="success" close onClose={onClose} aria-atomic="true">
      <AlertIcon><CircleCheck size={18} aria-hidden="true" /></AlertIcon>
      <AlertTitle>{content}</AlertTitle>
    </Alert>
  </div>;
}
