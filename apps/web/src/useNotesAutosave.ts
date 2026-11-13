import { useCallback, useEffect, useRef, useState } from 'react';

export type NotesSaveState = 'idle' | 'saving' | 'saved' | 'error';

const DEBOUNCE_MS = 1000;

/**
 * Salva as observações sozinho: 1 s depois da última tecla, ao sair do campo e ao sair da tela.
 * Só um envio roda por vez; ao terminar, se o texto mudou nesse meio tempo, envia de novo.
 * Se o envio falha, o texto continua no campo e o envio pode ser repetido com `flush`.
 */
export function useNotesAutosave(save: (text: string) => Promise<boolean>) {
  const [text, setTextState] = useState('');
  const [state, setState] = useState<NotesSaveState>('idle');
  const latest = useRef('');
  const saved = useRef('');
  const timer = useRef<number>(undefined);
  const running = useRef(false);
  const alive = useRef(true);
  const saveRef = useRef(save);
  useEffect(() => { saveRef.current = save; });

  /** Define o texto vindo do servidor (carga da lâmina), sem disparar envio. */
  const reset = useCallback((value: string) => {
    window.clearTimeout(timer.current);
    latest.current = value;
    saved.current = value;
    setTextState(value);
    setState('idle');
  }, []);

  const flush = useCallback(async () => {
    window.clearTimeout(timer.current);
    if (running.current) return;
    running.current = true;
    try {
      while (latest.current !== saved.current) {
        const sending = latest.current;
        if (alive.current) setState('saving');
        if (!(await saveRef.current(sending))) {
          if (alive.current) setState('error');
          return;
        }
        saved.current = sending;
      }
      if (alive.current) setState('saved');
    } finally {
      running.current = false;
    }
  }, []);

  const setText = useCallback((value: string) => {
    latest.current = value;
    setTextState(value);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => void flush(), DEBOUNCE_MS);
  }, [flush]);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      void flush();
    };
  }, [flush]);

  return { text, state, setText, reset, flush };
}
