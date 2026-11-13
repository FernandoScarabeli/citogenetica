package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.shared.storage.FormatoImagem;
import br.ufla.citogenetica.shared.storage.FotoPerfilStorage;
import br.ufla.citogenetica.shared.web.error.BusinessException;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


/**
 * Foto de perfil de cada conta, guardada no armazenamento de arquivos.
 * O formato é conferido pelos primeiros bytes do arquivo ({@link FormatoImagem}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FotoPerfilService {

    public static final int TAMANHO_MAXIMO_BYTES = 2 * 1024 * 1024;
    private static final String MENSAGEM_FORMATO = "Escolha uma imagem JPG, PNG ou WebP de até 2 MB.";

    private final UsuarioRepository usuarioRepository;
    private final FotoPerfilStorage storage;

    public record Foto(byte[] conteudo, String contentType) {}

    @Transactional
    public Usuario trocarMinhaFoto(byte[] conteudo) {
        FormatoImagem formato = FormatoImagem.detectar(conteudo, TAMANHO_MAXIMO_BYTES, MENSAGEM_FORMATO);
        Usuario usuario = usuarioAutenticado();
        String chaveAnterior = usuario.getFotoChave();
        String chaveNova = storage.salvarFotoPerfil(conteudo, formato.extensao(), formato.contentType());

        // A foto antiga só sai depois que o banco aponta para a nova; se o banco falhar, quem sai é a nova.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                apagarSemFalhar(status == STATUS_COMMITTED ? chaveAnterior : chaveNova);
            }
        });

        usuario.setFotoChave(chaveNova);
        return usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Foto minhaFoto() {
        String chave = usuarioAutenticado().getFotoChave();
        if (chave == null) {
            throw new NotFoundException("Esta conta não tem foto de perfil.");
        }
        return new Foto(storage.lerFotoPerfil(chave), FormatoImagem.daChave(chave).contentType());
    }

    private void apagarSemFalhar(String chave) {
        if (chave == null) {
            return;
        }
        try {
            storage.apagarFotoPerfil(chave);
        } catch (RuntimeException exception) {
            // A troca já foi resolvida; um arquivo que sobrou não pode virar erro para quem enviou a foto.
            log.warn("Não foi possível apagar a foto de perfil {} do armazenamento.", chave, exception);
        }
    }

    private Usuario usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            throw new AccessDeniedException("É necessário estar autenticado.");
        }
        return usuarioRepository.findById(principal.id())
                .orElseThrow(() -> new AccessDeniedException("É necessário estar autenticado."));
    }
}
