package br.ufla.citogenetica.notificacao.internal.application;

import java.util.Map;

import br.ufla.citogenetica.laboratorio.api.event.ConviteLaboratorioCriadoEvent;
import br.ufla.citogenetica.laboratorio.api.event.ConviteOrientacaoCriadoEvent;
import br.ufla.citogenetica.notificacao.internal.domain.TipoNotificacao;
import br.ufla.citogenetica.usuario.api.UsuarioConviteInfo;
import br.ufla.citogenetica.usuario.api.UsuarioConviteQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConviteLaboratorioNotificationListener {

    private final UsuarioConviteQuery usuarioConviteQuery;
    private final NotificacaoService notificacaoService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarConvite(ConviteLaboratorioCriadoEvent evento) {
        notificar(evento.emailDestino(), evento.conviteId(), "Convite para laboratório",
                "Você recebeu um convite para o laboratório “" + evento.laboratorioNome() + "”.",
                Map.of("conviteId", evento.conviteId().toString(),
                        "convite", "LABORATORIO",
                        "laboratorioId", evento.laboratorioId().toString(),
                        "papeis", evento.papeis()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarConviteOrientacao(ConviteOrientacaoCriadoEvent evento) {
        notificar(evento.emailDestino(), evento.conviteId(), "Convite de orientação",
                "Você recebeu um convite de orientação no laboratório “" + evento.laboratorioNome() + "”.",
                Map.of("conviteId", evento.conviteId().toString(),
                        "convite", "ORIENTACAO",
                        "laboratorioId", evento.laboratorioId().toString()));
    }

    private void notificar(String emailDestino, java.util.UUID conviteId, String titulo,
                           String descricao, Map<String, Object> dados) {
        UsuarioConviteInfo destinatario = usuarioConviteQuery
                .buscarPorEmailNormalizado(emailDestino).orElse(null);
        if (destinatario == null) {
            return;
        }

        try {
            notificacaoService.criar(destinatario.usuarioId(), TipoNotificacao.INFO, titulo, descricao, dados);
        } catch (RuntimeException exception) {
            log.warn("Não foi possível criar a notificação do convite {}.", conviteId);
        }
    }
}
