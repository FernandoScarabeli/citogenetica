package br.ufla.citogenetica.contagem.internal.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.ufla.citogenetica.contagem.internal.domain.RegistroContagem;
import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository;
import br.ufla.citogenetica.contagem.internal.persistence.RegistroContagemRepository.TipoDoRegistro;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Histórico paginado dos registros ativos de uma lâmina. Leitura igual à da lâmina ({@link AcessoLamina}). */
@Service
@RequiredArgsConstructor
public class HistoricoRegistroService {

    private final AcessoLamina acesso;
    private final RegistroContagemRepository registroRepository;

    /**
     * Do mais recente ao mais antigo (hora oficial e, no empate, id: a mesma ordem em que o desfazer consome).
     * Os tipos de toda a página vêm numa única consulta.
     */
    @Transactional(readOnly = true)
    public Page<RegistroHistorico> listar(UUID laminaId, UUID faseId, String statusBruto, Long usuarioId,
                                          int pagina, int tamanho) {
        StatusRegistro status = statusBruto == null ? null : ValidadorRegistro.status(statusBruto);
        acesso.carregarParaLeitura(laminaId, usuarioId);

        Specification<RegistroContagem> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            condicoes.add(cb.equal(raiz.get("laminaId"), laminaId));
            condicoes.add(cb.isNull(raiz.get("excluidoEm")));
            if (faseId != null) {
                condicoes.add(cb.equal(raiz.get("faseId"), faseId));
            }
            if (status != null) {
                condicoes.add(cb.equal(raiz.get("status"), status));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
        Sort ordem = Sort.by(Sort.Order.desc("registradoEm"), Sort.Order.desc("id"));
        Page<RegistroContagem> registros = registroRepository.findAll(filtro, PageRequest.of(pagina, tamanho, ordem));

        Map<UUID, List<TipoDoRegistro>> tiposPorRegistro = new HashMap<>();
        if (!registros.isEmpty()) {
            registroRepository.findTiposDosRegistros(registros.map(RegistroContagem::getId).getContent())
                    .forEach(tipo -> tiposPorRegistro.computeIfAbsent(tipo.getRegistroId(), k -> new ArrayList<>())
                            .add(tipo));
        }
        return new PageImpl<>(registros.getContent().stream().map(registro -> {
            List<TipoDoRegistro> tipos = tiposPorRegistro.getOrDefault(registro.getId(), List.of());
            List<UUID> tipoIds = tipos.stream().map(TipoDoRegistro::getTipoId).toList();
            return new RegistroHistorico(RegistroContagemService.resumir(registro, tipoIds),
                    tipos.stream().map(tipo -> new TipoNomeado(tipo.getTipoId(), tipo.getNome())).toList());
        }).toList(), registros.getPageable(), registros.getTotalElements());
    }
}
