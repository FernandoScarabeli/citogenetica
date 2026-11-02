package br.ufla.citogenetica.service;

import br.ufla.citogenetica.exception.NotFoundException;
import br.ufla.citogenetica.mapper.NotificacaoMapper;
import br.ufla.citogenetica.model.event.ServerEvent;
import br.ufla.citogenetica.model.notificacao.Notificacao;
import br.ufla.citogenetica.model.notificacao.dto.NotificacaoResponseDTO;
import br.ufla.citogenetica.model.type.TipoNotificacao;
import br.ufla.citogenetica.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoMapper notificacaoMapper;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public NotificacaoResponseDTO criar(TipoNotificacao tipo, String titulo, String mensagem, Map<String, Object> dados) {
        Notificacao notificacao = Notificacao.builder()
                .tipo(tipo)
                .titulo(titulo)
                .mensagem(mensagem)
                .dados(dados)
                .lida(false)
                .build();

        notificacao = notificacaoRepository.save(notificacao);
        NotificacaoResponseDTO responseDTO = notificacaoMapper.toResponseDTO(notificacao);

        eventPublisherService.publish(new ServerEvent(responseDTO));

        log.info("Notificação criada e publicada: {} - {}", tipo, titulo);
        return responseDTO;
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoResponseDTO> buscarTodas(Pageable pageable) {
        return notificacaoRepository.findAllByOrderByCreatedDateDesc(pageable)
                .map(notificacaoMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoResponseDTO> buscarNaoLidas(Pageable pageable) {
        return notificacaoRepository.findByLidaFalseOrderByCreatedDateDesc(pageable)
                .map(notificacaoMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas() {
        return notificacaoRepository.countByLidaFalse();
    }

    @Transactional
    public NotificacaoResponseDTO marcarComoLida(Long id) {
        Notificacao notificacao = notificacaoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notificação não encontrada com id: " + id));

        notificacao.setLida(true);
        notificacao = notificacaoRepository.save(notificacao);

        return notificacaoMapper.toResponseDTO(notificacao);
    }

    @Transactional
    public int marcarTodasComoLidas() {
        return notificacaoRepository.marcarTodasComoLidas();
    }
}

