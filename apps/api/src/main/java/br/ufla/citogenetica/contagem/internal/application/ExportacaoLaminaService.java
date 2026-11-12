package br.ufla.citogenetica.contagem.internal.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas.Dono;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas.LaminaExportada;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas.Protocolo;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas.RegistroExportado;
import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.domain.VersaoProtocolo;
import br.ufla.citogenetica.contagem.internal.persistence.ProtocoloRepository;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository.NomeTipoDoRegistro;
import br.ufla.citogenetica.contagem.internal.persistence.VersaoProtocoloRepository;
import br.ufla.citogenetica.usuario.api.UsuarioRosterInfo;
import br.ufla.citogenetica.usuario.api.UsuarioRosterQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportação das lâminas com a mesma regra de leitura da API: uma lâmina passa por {@link AcessoLamina} e várias
 * usam a mesma seleção da listagem ({@link LaminaService#listarTodasVisiveis}). Registros, tipos, donos e protocolos
 * são carregados em lote (sem N+1), em blocos para não estourar o limite de parâmetros do banco. REPEATABLE_READ
 * para que todas as consultas enxerguem o mesmo instante e o arquivo seja coerente.
 */
@Service
@RequiredArgsConstructor
public class ExportacaoLaminaService {

    /** Lâminas por consulta de registros e tipos; folgado em relação ao limite de parâmetros do driver. */
    private static final int TAMANHO_BLOCO = 500;

    private final AcessoLamina acesso;
    private final LaminaService laminaService;
    private final RegistroContagemRepository registroRepository;
    private final VersaoProtocoloRepository versaoRepository;
    private final ProtocoloRepository protocoloRepository;
    private final UsuarioRosterQuery usuarioRosterQuery;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportacaoLaminas exportar(UUID laminaId, Long usuarioId) {
        return montar(List.of(acesso.carregarParaLeitura(laminaId, usuarioId)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportacaoLaminas exportarVarias(UUID laboratorioId, Long donoFiltro, Long usuarioId) {
        return montar(laminaService.listarTodasVisiveis(laboratorioId, donoFiltro, usuarioId));
    }

    private ExportacaoLaminas montar(List<Lamina> laminas) {
        if (laminas.isEmpty()) {
            return new ExportacaoLaminas(clock.instant(), List.of());
        }
        Map<Long, UsuarioRosterInfo> donos = usuarioRosterQuery
                .buscarPorIds(laminas.stream().map(Lamina::getDonoUsuarioId).distinct().toList()).stream()
                .collect(Collectors.toMap(UsuarioRosterInfo::usuarioId, Function.identity()));
        Map<UUID, Protocolo> protocolos = protocolosPorVersao(laminas);

        List<UUID> laminaIds = laminas.stream().map(Lamina::getId).toList();
        Map<UUID, List<RegistroContagemRepository.RegistroExportado>> registrosPorLamina = new HashMap<>();
        Map<UUID, List<String>> tiposPorRegistro = new HashMap<>();
        for (int inicio = 0; inicio < laminaIds.size(); inicio += TAMANHO_BLOCO) {
            List<UUID> bloco = laminaIds.subList(inicio, Math.min(inicio + TAMANHO_BLOCO, laminaIds.size()));
            for (var registro : registroRepository.findAtivosParaExportacao(bloco)) {
                registrosPorLamina.computeIfAbsent(registro.getLaminaId(), id -> new ArrayList<>()).add(registro);
            }
            for (NomeTipoDoRegistro tipo : registroRepository.findNomesTiposParaExportacao(bloco)) {
                tiposPorRegistro.computeIfAbsent(tipo.getRegistroId(), id -> new ArrayList<>()).add(tipo.getNome());
            }
        }

        List<LaminaExportada> exportadas = laminas.stream().map(lamina -> {
            UsuarioRosterInfo dono = donos.get(lamina.getDonoUsuarioId());
            if (dono == null) {
                throw new IllegalStateException("Conta do dono da lâmina não encontrada.");
            }
            List<RegistroExportado> registros = registrosPorLamina.getOrDefault(lamina.getId(), List.of()).stream()
                    .map(registro -> new RegistroExportado(registro.getId(), registro.getFaseNome(),
                            registro.getStatus(), tiposPorRegistro.getOrDefault(registro.getId(), List.of()),
                            registro.getQuantidade(), registro.getRegistradoEm(), registro.getHoraAparelho(),
                            registro.getFusoAparelho(), registro.getVersion()))
                    .toList();
            return new LaminaExportada(lamina.getId(), lamina.getNome(), lamina.getEspecie(), lamina.getTratamento(),
                    lamina.getObservacoes(), lamina.getMeta(), lamina.getCreatedAt(), lamina.getLastModifiedAt(),
                    lamina.getVersion(), new Dono(dono.usuarioId(), dono.nome()), lamina.getLaboratorioId(),
                    protocolos.get(lamina.getProtocoloVersaoId()), registros);
        }).toList();
        return new ExportacaoLaminas(clock.instant(), exportadas);
    }

    /** Código, nome e número da versão de cada protocolo usado, em duas consultas (há poucos protocolos). */
    private Map<UUID, Protocolo> protocolosPorVersao(Collection<Lamina> laminas) {
        Map<UUID, VersaoProtocolo> versoes = versaoRepository
                .findAllById(laminas.stream().map(Lamina::getProtocoloVersaoId).distinct().toList()).stream()
                .collect(Collectors.toMap(VersaoProtocolo::getId, Function.identity()));
        var protocolos = protocoloRepository
                .findAllById(versoes.values().stream().map(VersaoProtocolo::getProtocoloId).distinct().toList())
                .stream().collect(Collectors.toMap(p -> p.getId(), Function.identity()));
        Map<UUID, Protocolo> resultado = new HashMap<>();
        versoes.forEach((id, versao) -> {
            var protocolo = protocolos.get(versao.getProtocoloId());
            resultado.put(id, new Protocolo(protocolo.getCodigo(), protocolo.getNome(), versao.getNumero()));
        });
        return resultado;
    }
}
