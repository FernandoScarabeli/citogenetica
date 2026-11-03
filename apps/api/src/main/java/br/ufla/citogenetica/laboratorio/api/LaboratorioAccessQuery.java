package br.ufla.citogenetica.laboratorio.api;

import java.util.UUID;

/** Consultas de autorização de dados do laboratório para módulos consumidores, como lâminas. */
public interface LaboratorioAccessQuery {

    boolean podeVerDadosDoPesquisador(UUID laboratorioId, Long pesquisadorUsuarioId, Long leitorUsuarioId);

    boolean podeEditarDadosDoPesquisador(UUID laboratorioId, Long pesquisadorUsuarioId, Long editorUsuarioId);
}
