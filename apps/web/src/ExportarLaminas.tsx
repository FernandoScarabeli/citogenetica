import { useEffect, useRef, useState } from 'react';
import { Braces, FileSpreadsheet } from 'lucide-react';
import type { ContagemApi } from './api/contagem-api';
import { mensagemDeErro } from './contagem-utils';
import { salvarArquivo } from './salvar-arquivo';

export type TipoExportacao = 'resumo' | 'registros' | 'json';
export const OPCOES_EXPORTACAO: { kind: TipoExportacao; rotulo: string; Icone: typeof Braces }[] = [
  { kind: 'resumo', rotulo: 'Resumo (CSV)', Icone: FileSpreadsheet },
  { kind: 'registros', rotulo: 'Registros (CSV)', Icone: FileSpreadsheet },
  { kind: 'json', rotulo: 'Dados (JSON)', Icone: Braces },
];

/** Baixa todas as lâminas do laboratório (ou as avulsas, sem laboratório) que a lista mostra. */
export function useExportarLaminas({ api, laboratorioId, donoUsuarioId, onError }: {
  api: ContagemApi;
  laboratorioId: string | null;
  donoUsuarioId?: number | null;
  onError: (message: string) => void;
}) {
  const [baixando, setBaixando] = useState<TipoExportacao | null>(null);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  async function exportar(kind: TipoExportacao) {
    if (baixando) return;
    setBaixando(kind);
    const filtros = { laboratorioId, ...(donoUsuarioId == null ? {} : { donoUsuarioId }) };
    try {
      const arquivo = await (kind === 'resumo' ? api.baixarLaminasResumoCsv(filtros)
        : kind === 'registros' ? api.baixarLaminasRegistrosCsv(filtros)
        : api.baixarLaminasJson(filtros));
      if (mounted.current) salvarArquivo(arquivo);
    } catch (error) {
      if (mounted.current) onError(mensagemDeErro(error, 'Não foi possível baixar o arquivo. Tente de novo.'));
    } finally {
      if (mounted.current) setBaixando(null);
    }
  }

  return { baixando, exportar };
}
