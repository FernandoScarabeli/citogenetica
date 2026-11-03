package br.ufla.citogenetica.service;

import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.mapper.NotificacaoMapper;
import br.ufla.citogenetica.model.event.NotificacaoCriadaEvent;
import br.ufla.citogenetica.model.notificacao.Notificacao;
import br.ufla.citogenetica.model.notificacao.dto.NotificacaoResponseDTO;
import br.ufla.citogenetica.model.type.TipoNotificacao;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.repository.NotificacaoRepository;
import br.ufla.citogenetica.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Notificações sempre pertencem a um destinatário. Os métodos de leitura e escrita recebem o id
 * do usuário autenticado e nunca operam fora dele.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacaoService {

    // Mesma mensagem para "não existe" e "é de outro usuário", para não revelar ids alheios.
    private static final String NOTIFICACAO_NAO_ENCONTRADA = "Notificação não encontrada.";

    private final NotificacaoRepository notificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoMapper notificacaoMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public NotificacaoResponseDTO criar(Long destinatarioId, TipoNotificacao tipo, String titulo,
                                        String mensagem, Map<String, Object> dados) {
        Usuario destinatario = usuarioRepository.findById(destinatarioId)
                .orElseThrow(() -> new NotFoundException(Usuario.class, destinatarioId));

        Notificacao notificacao = Notificacao.builder()
                .destinatario(destinatario)
                .tipo(tipo)
                .titulo(titulo)
                .mensagem(mensagem)
                .dados(dados)
                .lida(false)
                .build();

        notificacao = notificacaoRepository.save(notificacao);
        NotificacaoResponseDTO responseDTO = notificacaoMapper.toResponseDTO(notificacao);

        // O envio por SSE só acontece depois do commit (ver EventPublisherService).
        eventPublisher.publishEvent(new NotificacaoCriadaEvent(destinatarioId, responseDTO));

        log.info("Notificação {} criada para o usuário {}", notificacao.getId(), destinatarioId);
        return responseDTO;
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoResponseDTO> buscarTodas(Long destinatarioId, Pageable pageable) {
        return notificacaoRepository.findByDestinatarioIdOrderByCreatedDateDesc(destinatarioId, pageable)
                .map(notificacaoMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoResponseDTO> buscarNaoLidas(Long destinatarioId, Pageable pageable) {
        return notificacaoRepository.findByDestinatarioIdAndLidaFalseOrderByCreatedDateDesc(destinatarioId, pageable)
                .map(notificacaoMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoResponseDTO> buscarLidas(Long destinatarioId, Pageable pageable) {
        return notificacaoRepository.findByDestinatarioIdAndLidaTrueOrderByCreatedDateDesc(destinatarioId, pageable)
                .map(notificacaoMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Long destinatarioId) {
        return notificacaoRepository.countByDestinatarioIdAndLidaFalse(destinatarioId);
    }

    @Transactional
    public NotificacaoResponseDTO marcarComoLida(Long destinatarioId, Long id) {
        Notificacao notificacao = notificacaoRepository.findByIdAndDestinatarioId(id, destinatarioId)
                .orElseThrow(() -> new NotFoundException(NOTIFICACAO_NAO_ENCONTRADA));

        notificacao.setLida(true);
        notificacao = notificacaoRepository.save(notificacao);

        return notificacaoMapper.toResponseDTO(notificacao);
    }

    @Transactional
    public int marcarTodasComoLidas(Long destinatarioId) {
        return notificacaoRepository.marcarTodasComoLidas(destinatarioId);
    }
}
