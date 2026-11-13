package br.ufla.citogenetica.contagem.internal.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.domain.AnotacaoFoto;
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
                       Long enviadaPorUsuarioId, String enviadaPorNome, Instant enviadaEm, List<AnotacaoFoto> anotacoes) {}

    public static final int MAXIMO_ANOTACOES = 100;
    private static final int TEXTO_ANOTACAO_MAXIMO = 80;
    private static final double TAMANHO_TEXTO_MINIMO = 0.4;
    private static final double TAMANHO_TEXTO_MAXIMO = 4;
    /** Nomes das cores da primeira versão; marcações novas usam qualquer cor em hex (#rrggbb). */
    private static final Set<String> CORES_ANTIGAS = Set.of("azul", "amarelo", "vermelho", "branco", "preto");
    private static final Pattern COR_HEX = Pattern.compile("#[0-9a-fA-F]{6}");

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

    /** Edita legenda e marcações depois de publicada. Mesma permissão de enviar. */
    @Transactional
    public Foto editar(UUID laminaId, UUID fotoId, String legendaBruta, List<AnotacaoFoto> anotacoesBrutas, Long usuarioId) {
        acesso.carregarParaEscrita(laminaId, usuarioId);
        FotoLamina foto = repository.findByIdAndLaminaId(fotoId, laminaId)
                .orElseThrow(() -> new NotFoundException(FOTO_NAO_ENCONTRADA));
        foto.editar(legenda(legendaBruta), anotacoes(anotacoesBrutas));
        return comNomes(List.of(repository.saveAndFlush(foto))).get(0);
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

    private static List<AnotacaoFoto> anotacoes(List<AnotacaoFoto> brutas) {
        if (brutas == null) {
            return List.of();
        }
        if (brutas.size() > MAXIMO_ANOTACOES) {
            throw new BusinessException("Uma foto pode ter até " + MAXIMO_ANOTACOES + " marcações.");
        }
        return brutas.stream().map(FotoLaminaService::anotacao).toList();
    }

    private static AnotacaoFoto anotacao(AnotacaoFoto bruta) {
        if (bruta == null || bruta.tipo() == null || cor(bruta.cor()) == null) {
            throw new BusinessException("Marcação inválida.");
        }
        String cor = cor(bruta.cor());
        exigirCoordenada(bruta.x1());
        exigirCoordenada(bruta.y1());
        return switch (bruta.tipo()) {
            case "SETA" -> {
                if (bruta.x2() == null || bruta.y2() == null) {
                    throw new BusinessException("Marcação inválida.");
                }
                exigirCoordenada(bruta.x2());
                exigirCoordenada(bruta.y2());
                yield new AnotacaoFoto("SETA", bruta.x1(), bruta.y1(), bruta.x2(), bruta.y2(), null, cor, null);
            }
            case "TEXTO" -> {
                String texto = bruta.texto() == null ? "" : bruta.texto().strip();
                if (texto.isEmpty() || texto.length() > TEXTO_ANOTACAO_MAXIMO) {
                    throw new BusinessException("O texto da marcação deve ter de 1 a " + TEXTO_ANOTACAO_MAXIMO + " caracteres.");
                }
                Double tamanho = bruta.tamanho();
                if (tamanho != null && (!Double.isFinite(tamanho) || tamanho < TAMANHO_TEXTO_MINIMO || tamanho > TAMANHO_TEXTO_MAXIMO)) {
                    throw new BusinessException("Tamanho do texto da marcação inválido.");
                }
                yield new AnotacaoFoto("TEXTO", bruta.x1(), bruta.y1(), null, null, texto, cor, tamanho);
            }
            default -> throw new BusinessException("Marcação inválida.");
        };
    }

    private static String cor(String bruta) {
        if (bruta == null) {
            return null;
        }
        if (CORES_ANTIGAS.contains(bruta)) {
            return bruta;
        }
        return COR_HEX.matcher(bruta).matches() ? bruta.toLowerCase(Locale.ROOT) : null;
    }

    private static void exigirCoordenada(Double valor) {
        if (valor == null || !Double.isFinite(valor) || valor < 0 || valor > 1) {
            throw new BusinessException("Marcação fora da foto.");
        }
    }

    private List<Foto> comNomes(List<FotoLamina> fotos) {
        Map<Long, String> nomes = usuarioRosterQuery.buscarPorIds(fotos.stream().map(FotoLamina::getEnviadaPorUsuarioId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(UsuarioRosterInfo::usuarioId, UsuarioRosterInfo::nome, (a, b) -> a));
        return fotos.stream().map(foto -> new Foto(foto.getId(), foto.getLegenda(), foto.getTamanhoBytes(),
                FormatoImagem.daChave(foto.getChave()).contentType(), foto.getEnviadaPorUsuarioId(),
                nomes.getOrDefault(foto.getEnviadaPorUsuarioId(), ""), foto.getCreatedAt(),
                List.copyOf(foto.getAnotacoes()))).toList();
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
