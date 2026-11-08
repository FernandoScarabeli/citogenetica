import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { Archive, Download, Upload } from 'lucide-react';
import { typeClass } from '@/lib/type-scale';
import type { ContagemApi } from './api/contagem-api';
import { isState, slidesForLaboratory, type Slide } from './domain';
import { mesclarBackup, migrarLaminas, type ProgressoMigracao, type ResultadoMigracao } from './migracao-laminas';
import { download, load, save } from './storage';

const plural = (n: number, um: string, varios: string) => `${n.toLocaleString('pt-BR')} ${n === 1 ? um : varios}`;
const lerLocal = (laboratorioId: string | null) => {
  const loaded = load();
  return { error: loaded.error ?? '', slides: loaded.error ? [] : slidesForLaboratory(loaded.state.slides, laboratorioId) };
};

/** Lâminas guardadas só neste navegador: backup, importação de backup e envio ao servidor. */
export function LocalSlidesPanel({ api, usuarioId, laboratorioId, onFinished }: {
  api: ContagemApi;
  usuarioId: number;
  laboratorioId: string | null;
  /** Chamado ao fim do envio, com o resultado, para a lista recarregar. */
  onFinished: (result: ResultadoMigracao) => void;
}) {
  const [local, setLocal] = useState(() => lerLocal(laboratorioId));
  const [backedUp, setBackedUp] = useState(false);
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState<ProgressoMigracao | null>(null);
  const [announce, setAnnounce] = useState('');
  const [message, setMessage] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!running) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [running]);

  const refresh = () => setLocal(lerLocal(laboratorioId));

  function backup() {
    const current = load();
    if (current.error) { setMessage(current.error); return; }
    const day = new Date();
    const stamp = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`;
    try {
      download(`citogenetica-backup-${stamp}.json`, JSON.stringify(current.state, null, 2), 'application/json');
    } catch {
      setMessage('Não foi possível baixar o backup. Nada foi enviado.');
      return;
    }
    setBackedUp(true);
    setMessage('Backup baixado. Guarde o arquivo em um lugar seguro antes de enviar.');
  }

  async function importBackup(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    let parsed: unknown;
    try {
      parsed = JSON.parse(await file.text());
    } catch {
      setMessage('Não foi possível ler o arquivo. Nada foi alterado.');
      return;
    }
    if (!isState(parsed)) { setMessage('Este arquivo não é um backup válido. Nada foi alterado.'); return; }
    const current = load();
    if (current.error) { setMessage(current.error); return; }
    const merged = mesclarBackup(current.state, parsed);
    if (merged.adicionadas > 0 && !save(merged.state)) {
      setMessage('Não foi possível gravar neste navegador. Nada foi alterado.');
      return;
    }
    if (merged.adicionadas > 0) setBackedUp(false);
    refresh();
    setMessage(merged.adicionadas === 0
      ? 'Todas as lâminas do arquivo já estavam neste navegador. Nada foi alterado.'
      : `${plural(merged.adicionadas, 'lâmina voltou', 'lâminas voltaram')} para este navegador. As que já existiam não foram alteradas.`);
  }

  async function send() {
    if (running || !backedUp) return;
    const current = lerLocal(laboratorioId);
    if (current.error) { setLocal(current); return; }
    setMessage('');
    setRunning(true);
    let result: ResultadoMigracao;
    try {
      result = await migrarLaminas({
        api, usuarioId, laboratorioId, laminas: current.slides,
        onProgresso: next => {
          setProgress(next);
          setAnnounce(`Enviando lâmina ${next.laminaIndice} de ${next.laminaTotal}: ${next.laminaNome}.`);
        },
      });
    } finally {
      setRunning(false);
      setProgress(null);
      setAnnounce('');
    }
    refresh();
    onFinished(result);
  }

  if (local.error) return <section className="local-slides" aria-labelledby="local-slides-title"><h2 id="local-slides-title" className={typeClass('title')}>Lâminas neste navegador</h2><p role="alert" className="local-slides-error">{local.error}</p></section>;
  if (local.slides.length === 0 && !message) return null;

  const names = (slides: Slide[]) => slides.map(slide => <li key={slide.id}><strong>{slide.name}</strong><span>{plural(slide.entries.length, 'célula', 'células')}</span></li>);
  return <section className="local-slides" aria-labelledby="local-slides-title">
    <div className="local-slides-head">
      <span className="local-slides-symbol"><Archive size={20} aria-hidden="true" /></span>
      <div>
        <h2 id="local-slides-title" className={typeClass('title')}>{local.slides.length > 0 ? `${plural(local.slides.length, 'lâmina guardada', 'lâminas guardadas')} só neste navegador` : 'Lâminas neste navegador'}</h2>
        {local.slides.length > 0 && <p>Elas não aparecem para mais ninguém e podem se perder se os dados do navegador forem apagados. Baixe o backup e envie para o servidor; cada lâmina só sai daqui depois que o servidor confirmar todas as células.</p>}
      </div>
    </div>
    {local.slides.length > 0 && <ul className="local-slides-list">{names(local.slides)}</ul>}
    <div className="local-slides-actions">
      <button className="button secondary" type="button" onClick={backup} disabled={running || local.slides.length === 0}><Download size={16} aria-hidden="true" /> Baixar backup</button>
      <button className="button primary" type="button" onClick={() => void send()} disabled={running || !backedUp || local.slides.length === 0} aria-describedby="local-slides-hint">Enviar para o servidor</button>
      <button className="button secondary" type="button" onClick={() => fileInput.current?.click()} disabled={running}><Upload size={16} aria-hidden="true" /> Importar backup</button>
      <input ref={fileInput} className="sr-only" type="file" accept="application/json,.json" aria-label="Arquivo de backup" tabIndex={-1} onChange={event => void importBackup(event)} />
    </div>
    {!backedUp && local.slides.length > 0 && <p id="local-slides-hint" className="local-slides-hint">O envio é liberado depois que você baixar o backup.</p>}
    {running && progress && <div className="local-slides-progress">
      <p aria-hidden="true">Lâmina {progress.laminaIndice} de {progress.laminaTotal} · {progress.celulasEnviadas.toLocaleString('pt-BR')} de {progress.celulasTotal.toLocaleString('pt-BR')} células enviadas</p>
      <div className="progress-track" aria-hidden="true"><span style={{ width: `${progress.celulasTotal ? (progress.celulasEnviadas / progress.celulasTotal) * 100 : 100}%` }} /></div>
    </div>}
    <p className="sr-only" role="status" aria-live="polite">{announce}</p>
    {message && <p className="local-slides-message" role="status">{message}</p>}
  </section>;
}
