import { useCallback, useEffect, useRef, useState } from 'react';
import type { ContagemApi } from '../api/contagem-api';
import type { CriarRegistroRequestDTO } from '../api/contracts/contagem';
import { isUnauthorized } from '../api/errors';
import {
  INTERVALO_REENVIO_MS, ehRecusaDefinitiva, guardarRegistro, lerFila, removerRegistro, type RegistroGuardado,
} from './fila-registros';

interface Opcoes {
  api: Pick<ContagemApi, 'criarRegistro'>;
  usuarioId: number;
  laminaId: string;
  /** Só reenvia depois de a lâmina estar aberta na tela. */
  ativo: boolean;
  /** Uma célula guardada foi recusada de vez e saiu da fila. */
  onRecusado: (item: RegistroGuardado, erro: unknown) => void;
  /** A sessão expirou; a fila foi mantida. */
  onSessaoExpirada: () => void;
  /** Terminou uma rodada que mudou algo no servidor ou na fila; hora de recarregar o resumo. */
  onConcluido: () => void;
}

/** Fila persistente de células contadas sem conexão, com reenvio automático, um registro por vez e em ordem. */
export function useFilaRegistros({ api, usuarioId, laminaId, ativo, onRecusado, onSessaoExpirada, onConcluido }: Opcoes) {
  const [itens, setItens] = useState<RegistroGuardado[]>(() => lerFila(usuarioId, laminaId));
  const [enviando, setEnviando] = useState(false);
  const drenando = useRef(false);
  const montado = useRef(true);
  const chamadas = useRef({ onRecusado, onSessaoExpirada, onConcluido });
  useEffect(() => { chamadas.current = { onRecusado, onSessaoExpirada, onConcluido }; });
  useEffect(() => { montado.current = true; return () => { montado.current = false; }; }, []);

  const sincronizar = useCallback(() => {
    if (montado.current) setItens(lerFila(usuarioId, laminaId));
  }, [usuarioId, laminaId]);

  // A fila vale por pessoa e lâmina: ao trocar, relê do aparelho.
  useEffect(sincronizar, [sincronizar]);

  /** Guarda a célula no aparelho. Devolve false se não foi possível guardar. */
  const enfileirar = useCallback((pedido: CriarRegistroRequestDTO): boolean => {
    const guardado = guardarRegistro({ usuarioId, laminaId, pedido });
    if (guardado) sincronizar();
    return guardado;
  }, [usuarioId, laminaId, sincronizar]);

  const reenviar = useCallback(async () => {
    if (!ativo || drenando.current || lerFila(usuarioId, laminaId).length === 0) return;
    drenando.current = true;
    setEnviando(true);
    let mudou = false;
    try {
      for (let item = lerFila(usuarioId, laminaId)[0]; item && montado.current; item = lerFila(usuarioId, laminaId)[0]) {
        try {
          await api.criarRegistro(laminaId, item.pedido);
          removerRegistro(item.pedido.id);
          mudou = true;
        } catch (erro) {
          if (ehRecusaDefinitiva(erro)) {
            removerRegistro(item.pedido.id);
            mudou = true;
            chamadas.current.onRecusado(item, erro);
          } else {
            // Sem conexão, 5xx, 408 e 429: mantém tudo e tenta de novo na próxima rodada. 401: a sessão expirou.
            if (isUnauthorized(erro)) chamadas.current.onSessaoExpirada();
            break;
          }
        }
        sincronizar();
      }
    } finally {
      drenando.current = false;
      if (montado.current) setEnviando(false);
      sincronizar();
    }
    if (mudou && montado.current) chamadas.current.onConcluido();
  }, [api, ativo, usuarioId, laminaId, sincronizar]);

  // Ao abrir a lâmina (e quando ela termina de carregar).
  useEffect(() => { void reenviar(); }, [reenviar]);

  // Ao voltar a conexão e, enquanto houver fila, de tempos em tempos.
  useEffect(() => {
    window.addEventListener('online', reenviar);
    return () => window.removeEventListener('online', reenviar);
  }, [reenviar]);
  useEffect(() => {
    if (!itens.length) return;
    const relogio = window.setInterval(() => void reenviar(), INTERVALO_REENVIO_MS);
    return () => window.clearInterval(relogio);
  }, [itens.length, reenviar]);

  return { itens, enviando, enfileirar };
}
