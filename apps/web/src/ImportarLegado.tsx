import { useEffect, useRef, useState, type ChangeEvent, type DragEvent } from 'react';
import { FileJson } from 'lucide-react';
import type { ContagemApi } from './api/contagem-api';
import type { ProtocoloDTO } from './api/contracts/contagem';
import { mensagemDeErro } from './contagem-utils';
import { importarLaminasLegadas, type ProgressoImportacao, type ResultadoImportacao } from './importacao-legado';
import { ErroDeArquivoLegado, lerBackupLegado, tiposPersonalizados, type LaminaLegada, type LeituraLegado } from './leitura-legado';
import { Modal } from './SlideWidgets';

type Fase = 'escolher' | 'lendo' | 'previa' | 'importando' | 'resultado';

const plural = (n: number, singular: string, mais: string) => `${n.toLocaleString('pt-BR')} ${n === 1 ? singular : mais}`;
const rotuloProtocolo = (codigo: LaminaLegada['protocolo']) => (codigo === 'mitose' ? 'Mitose' : 'Meiose');

/**
 * Traz para o sistema as lâminas dos backups JSON dos registros antigos (HTML solto).
 * A lâmina vai para a lista onde a pessoa está (um laboratório ou as avulsas). Nada fica guardado no navegador.
 * É só o diálogo: monte o componente para abrir e desmonte em onClose.
 */
export function ImportarLegado({ api, laboratorioNome, laboratorioId, usuarioId, protocolos, onFinished, onClose }: {
  api: ContagemApi;
  laboratorioNome: string | null;
  laboratorioId: string | null;
  usuarioId: number;
  protocolos: ProtocoloDTO[];
  /** Chamado quando uma importação termina (com ou sem falhas) para a lista recarregar. */
  onFinished: () => void;
  onClose: () => void;
}) {
  const [fase, setFase] = useState<Fase>('escolher');
  const [erro, setErro] = useState('');
  const [leitura, setLeitura] = useState<LeituraLegado | null>(null);
  const [nomeArquivo, setNomeArquivo] = useState('');
  const [progresso, setProgresso] = useState<ProgressoImportacao | null>(null);
  const [resultado, setResultado] = useState<ResultadoImportacao | null>(null);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  const destino = laboratorioNome ? `no laboratório ${laboratorioNome}` : 'como lâminas avulsas';

  function fechar() {
    if (fase === 'importando' || fase === 'lendo') return;
    onClose();
  }

  const [arrastando, setArrastando] = useState(false);

  function escolher(event: ChangeEvent<HTMLInputElement>) {
    const arquivo = event.target.files?.[0];
    event.target.value = '';
    if (arquivo) void ler(arquivo);
  }

  function soltar(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setArrastando(false);
    const arquivo = event.dataTransfer.files?.[0];
    if (arquivo && fase !== 'lendo') void ler(arquivo);
  }

  async function ler(arquivo: File) {
    setErro(''); setFase('lendo'); setNomeArquivo(arquivo.name);
    try {
      const lida = await lerBackupLegado(arquivo);
      if (!mounted.current) return;
      setLeitura(lida);
      setFase('previa');
    } catch (error) {
      if (!mounted.current) return;
      setErro(error instanceof ErroDeArquivoLegado ? error.message : 'Não foi possível ler o arquivo. Tente de novo.');
      setFase('escolher');
    }
  }

  async function importar() {
    if (!leitura || leitura.laminas.length === 0) return;
    setFase('importando'); setErro(''); setProgresso(null);
    try {
      const final = await importarLaminasLegadas({
        api, usuarioId, destino: laboratorioId, laminas: leitura.laminas, onProgresso: atual => { if (mounted.current) setProgresso(atual); },
      });
      if (!mounted.current) return;
      setResultado(final);
      setFase('resultado');
    } catch (error) {
      if (!mounted.current) return;
      setErro(mensagemDeErro(error, 'A importação foi interrompida. Importar de novo é seguro: nada será duplicado.'));
      setFase('previa');
    } finally {
      onFinished();
    }
  }

  const importadas = resultado?.importadas ?? [];
  const novas = importadas.filter(item => item.nova);
  const existentes = importadas.filter(item => !item.nova);
  const conflitos = importadas.reduce((soma, item) => soma + item.celulasEmConflito, 0);
  const incompleta = !!resultado && (resultado.falhas.length > 0 || resultado.interrupcao !== null || resultado.naoTentadas > 0);

  return <Modal title="Importar backup antigo" onClose={fechar} locked={fase === 'importando' || fase === 'lendo'}>
      <div className="importar-legado">
        {fase === 'escolher' || fase === 'lendo' ? <>
          <p className="modal-description">Escolha o arquivo de backup (.json) baixado dos registros antigos. As lâminas dele serão importadas {destino}. Importar o mesmo arquivo de novo não duplica nada.</p>
          <label className={`importar-arquivo${arrastando ? ' is-dragging' : ''}${fase === 'lendo' ? ' is-busy' : ''}`}
            onDragOver={event => { event.preventDefault(); setArrastando(true); }} onDragLeave={() => setArrastando(false)} onDrop={soltar}>
            <FileJson size={26} aria-hidden="true" />
            <strong>Escolher arquivo de backup (.json)</strong>
            <span>ou arraste o arquivo para cá</span>
            <input className="sr-only" type="file" accept=".json,application/json" disabled={fase === 'lendo'} onChange={escolher} aria-describedby={erro ? 'importar-erro' : undefined} />
          </label>
          {fase === 'lendo' && <p role="status">Lendo {nomeArquivo}…</p>}
          {erro && <p className="select-error" id="importar-erro" role="alert">{erro}</p>}
          <div className="modal-actions"><button type="button" className="button secondary" onClick={fechar}>Cancelar</button></div>
        </> : fase === 'previa' && leitura ? <>
          <p className="modal-description">{nomeArquivo}: {plural(leitura.laminas.length, 'lâmina pode ser importada', 'lâminas podem ser importadas')} {destino}.</p>
          {leitura.laminas.length > 0 && <ul className="importar-lista" aria-label="Lâminas que serão importadas">
            {leitura.laminas.map(lamina => {
              const personalizados = tiposPersonalizados(lamina, protocolos);
              return <li key={`${lamina.protocolo}|${lamina.nome}`}>
                <strong>{lamina.nome}</strong>
                <span className="importar-detalhe">{rotuloProtocolo(lamina.protocolo)} · {plural(lamina.celulas.length, 'célula', 'células')}</span>
                {lamina.renomeados.length > 0 && <span className="importar-aviso">{lamina.renomeados.map(item => `"${item.de}" será importado como "${item.para}"`).join('; ')}.</span>}
                {personalizados.length > 0 && <span className="importar-aviso">{plural(personalizados.length, 'tipo de anormalidade não existe', 'tipos de anormalidade não existem')} no protocolo e {personalizados.length === 1 ? 'será criado como tipo personalizado' : 'serão criados como tipos personalizados'}: {personalizados.join('; ')}.</span>}
              </li>;
            })}
          </ul>}
          {leitura.recusadas.length > 0 && <>
            <h3 className="importar-subtitulo">{plural(leitura.recusadas.length, 'lâmina não será importada', 'lâminas não serão importadas')}</h3>
            <ul className="importar-lista" aria-label="Lâminas que não serão importadas">
              {leitura.recusadas.map(item => <li key={item.nome}><strong>{item.nome}</strong><span className="importar-aviso">{item.motivo}</span></li>)}
            </ul>
          </>}
          {leitura.laminas.length === 0 && <p className="select-error" role="alert">Nenhuma lâmina deste arquivo pode ser importada.</p>}
          {erro && <p className="select-error" role="alert">{erro}</p>}
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={() => { setLeitura(null); setErro(''); setFase('escolher'); }}>Escolher outro arquivo</button>
            <button type="button" className="button primary" disabled={leitura.laminas.length === 0} onClick={() => void importar()}>Importar {plural(leitura.laminas.length, 'lâmina', 'lâminas')}</button>
          </div>
        </> : fase === 'importando' ? <>
          <p className="modal-description">Importando {destino}. Mantenha esta janela aberta até terminar.</p>
          <p role="status" aria-live="polite">{progresso ? `Lâmina ${progresso.laminaIndice} de ${progresso.laminaTotal}: ${progresso.laminaNome}` : 'Preparando a importação…'}</p>
          {progresso && <>
            <progress className="importar-barra" max={progresso.celulasTotal} value={progresso.celulasEnviadas} aria-label={`Células da lâmina ${progresso.laminaNome}`} />
            <p className="importar-detalhe" aria-hidden="true">{progresso.celulasEnviadas.toLocaleString('pt-BR')} de {progresso.celulasTotal.toLocaleString('pt-BR')} células</p>
          </>}
        </> : resultado ? <>
          <p className="modal-description" role="status">{incompleta ? 'A importação não terminou por completo.' : 'Importação concluída.'}</p>
          {resultado.interrupcao && <p className="select-error" role="alert">{resultado.interrupcao} Nada foi perdido: use Tentar de novo (ou escolha o mesmo arquivo depois) e o que já foi importado não se repete.</p>}
          <ul className="importar-lista" aria-label="Resultado da importação">
            {novas.length > 0 && <li><strong>{plural(novas.length, 'lâmina nova', 'lâminas novas')}</strong><span className="importar-detalhe">{novas.map(item => `${item.nome} (${plural(item.celulasConfirmadas, 'célula', 'células')})`).join('; ')}</span></li>}
            {existentes.length > 0 && <li><strong>{plural(existentes.length, 'lâmina já estava no sistema', 'lâminas já estavam no sistema')}</strong><span className="importar-detalhe">{existentes.map(item => `${item.nome} (${plural(item.celulasAcrescentadas, 'célula acrescentada', 'células acrescentadas')})`).join('; ')}</span></li>}
            {conflitos > 0 && <li><strong>{plural(conflitos, 'célula', 'células')} {conflitos === 1 ? 'foi alterada' : 'foram alteradas'} no arquivo depois da primeira importação</strong><span className="importar-detalhe">Ficou valendo o que já está no sistema.</span></li>}
            {resultado.falhas.length > 0 && <li><strong>{plural(resultado.falhas.length, 'lâmina com falha', 'lâminas com falha')}</strong>{resultado.falhas.map(item => <span className="importar-aviso" key={item.nome}>{item.nome}: {item.motivo}</span>)}</li>}
            {resultado.naoTentadas > 0 && <li><strong>{plural(resultado.naoTentadas, 'lâmina não foi tentada', 'lâminas não foram tentadas')}</strong></li>}
            {importadas.length === 0 && resultado.falhas.length === 0 && resultado.naoTentadas === 0 && <li><strong>Nada a importar.</strong></li>}
          </ul>
          <div className="modal-actions">
            {incompleta && <button type="button" className="button secondary" onClick={() => void importar()}>Tentar de novo</button>}
            <button type="button" className="button primary" onClick={fechar}>Fechar</button>
          </div>
        </> : null}
      </div>
    </Modal>;
}
