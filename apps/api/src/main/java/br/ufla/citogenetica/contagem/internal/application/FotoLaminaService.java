package br.ufla.citogenetica.contagem.internal.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.domain.FotoLamina;
import br.ufla.citogenetica.contagem.internal.domain.Lamina;
import br.ufla.citogenetica.contagem.internal.persistence.FotoLaminaRepository;
import br.ufla.citogenetica.shared.persistence.UuidFactory;
import br.ufla.citogenetica.shared.storage.FormatoImagem;
import br.ufla.citogenetica.shared.storage.FotoLaminaStorage;
import br.ufla.citogenetica.shared.web.error.BusinessException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.usuario.api.UsuarioRosterInfo;
import br.ufla.citogenetica.usuario.api.UsuarioRosterQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fotos de uma lâmina. Ver segue a leitura da lâmina; enviar e apagar seguem a escrita ({@link AcessoLamina}).
 * O arquivo vai para o armazenamento antes do commit; se o banco falhar, ele é apagado, e só depois do commit
 * uma foto removida sai do armazenamento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FotoLaminaService {

    public static final int TAMANHO_MAXIMO_BYTES = 10 * 1024 * 1024;
    public static final int MAXIMO_POR_LAMINA = 50;
    private static final int LEGENDA_MAXIMA = 200;
    private static final String MENSAGEM_FORMATO = "Escolha uma imagem JPG, PNG ou WebP de até 10 MB.";
    private static final String FOTO_NAO_ENCONTRADA = "Foto não encontrada.";

    private final AcessoLamina acesso;
    private final FotoLaminaRepository repository;
    private final FotoLaminaStorage storage;
    private final UsuarioRosterQuery usuarioRosterQuery;
    private final UuidFactory uuidFactory;

    public record Foto(UUID id, String legenda, int tamanhoBytes, String contentType,
                       Long enviadaPorUsuarioId, String enviadaPorNome, Instant enviadaEm) {}

    public record Arquivo(byte[] conteudo, String contentType) {}

    @Transactional(readOnly = true)
    public List<Foto> listar(UUID laminaId, Long usuarioId) {
        acesso.carregarParaLeitura(laminaId, usuarioId);
        return comNomes(repository.findByLaminaIdOrderByCreatedAtAscIdAsc(laminaId));
    }

    @Transactional
    public Foto enviar(UUID laminaId, byte[] conteudo, String legendaBruta, Long usuarioId) {
        Lamina lamina = acesso.carregarParaEscrita(laminaId, usuarioId);
        FormatoImagem formato = FormatoImagem.detectar(conteudo, TAMANHO_MAXIMO_BYTES, MENSAGEM_FORMATO);
        String legenda = legenda(legendaBruta);
        if (repository.countByLaminaId(lamina.getId()) >= MAXIMO_POR_LAMINA) {
            throw new BusinessException("Esta lâmina já tem " + MAXIMO_POR_LAMINA + " fotos. Apague alguma antes de enviar outra.");
        }

        String chave = storage.salvarFotoLamina(conteudo, formato);
        aoTerminar(status -> { if (status != TransactionSynchronization.STATUS_COMMITTED) apagarSemFalhar(chave); });
        FotoLamina foto = repository.saveAndFlush(new FotoLamina(uuidFactory.generate(), lamina.getId(), chave,
                conteudo.length, legenda, usuarioId));
        return comNomes(List.of(foto)).get(0);
    }

    @Transactional(readOnly = true)
    public Arquivo arquivo(UUID laminaId, UUID fotoId, Long usuarioId) {
        acesso.carregarParaLeitura(laminaId, usuarioId);
        FotoLamina foto = repository.findByIdAndLaminaId(fotoId, laminaId)
                .orElseThrow(() -> new NotFoundException(FOTO_NAO_ENCONTRADA));
        return new Arquivo(storage.lerFotoLamina(foto.getChave()), FormatoImagem.daChave(foto.getChave()).contentType());
    }

    @Transactional
    public void apagar(UUID laminaId, UUID fotoId, Long usuarioId) {
        acesso.carregarParaEscrita(laminaId, usuarioId);
        FotoLamina foto = repository.findByIdAndLaminaId(fotoId, laminaId)
                .orElseThrow(() -> new NotFoundException(FOTO_NAO_ENCONTRADA));
        repository.delete(foto);
        String chave = foto.getChave();
        aoTerminar(status -> { if (status == TransactionSynchronization.STATUS_COMMITTED) apagarSemFalhar(chave); });
    }

    private static String legenda(String bruta) {
        if (bruta == null || bruta.isBlank()) {
            return null;
        }
        String legenda = bruta.strip();
        if (legenda.length() > LEGENDA_MAXIMA) {
            throw new BusinessException("A legenda pode ter até " + LEGENDA_MAXIMA + " caracteres.");
        }
        return legenda;
    }

    private List<Foto> comNomes(List<FotoLamina> fotos) {
        Map<Long, String> nomes = usuarioRosterQuery.buscarPorIds(fotos.stream().map(FotoLamina::getEnviadaPorUsuarioId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(UsuarioRosterInfo::usuarioId, UsuarioRosterInfo::nome, (a, b) -> a));
        return fotos.stream().map(foto -> new Foto(foto.getId(), foto.getLegenda(), foto.getTamanhoBytes(),
                FormatoImagem.daChave(foto.getChave()).contentType(), foto.getEnviadaPorUsuarioId(),
                nomes.getOrDefault(foto.getEnviadaPorUsuarioId(), ""), foto.getCreatedAt())).toList();
    }

    private static void aoTerminar(java.util.function.IntConsumer acao) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                acao.accept(status);
            }
        });
    }

    private void apagarSemFalhar(String chave) {
        try {
            storage.apagarFotoLamina(chave);
        } catch (RuntimeException exception) {
            log.warn("Não foi possível apagar a foto {} do armazenamento.", chave, exception);
        }
    }
}
