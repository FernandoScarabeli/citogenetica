package br.ufla.citogenetica.usuario.internal.application;

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

import java.util.Arrays;

/**
 * Foto de perfil de cada conta, guardada no armazenamento de arquivos.
 * O formato é conferido pelos primeiros bytes do arquivo, e não pelo nome ou pelo tipo informado pelo navegador.
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

    private enum Formato {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        final String extensao;
        final String contentType;

        Formato(String extensao, String contentType) {
            this.extensao = extensao;
            this.contentType = contentType;
        }

        static Formato daExtensao(String chave) {
            String extensao = chave.substring(chave.lastIndexOf('.') + 1);
            return Arrays.stream(values())
                    .filter(formato -> formato.extensao.equals(extensao))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Extensão de foto desconhecida: " + extensao));
        }
    }

    @Transactional
    public Usuario trocarMinhaFoto(byte[] conteudo) {
        Formato formato = detectarFormato(conteudo);
        Usuario usuario = usuarioAutenticado();
        String chaveAnterior = usuario.getFotoChave();
        String chaveNova = storage.salvarFotoPerfil(conteudo, formato.extensao, formato.contentType);

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
        return new Foto(storage.lerFotoPerfil(chave), Formato.daExtensao(chave).contentType);
    }

    static Formato detectarFormato(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0 || conteudo.length > TAMANHO_MAXIMO_BYTES) {
            throw new BusinessException(MENSAGEM_FORMATO);
        }
        if (comecaCom(conteudo, 0, 0xFF, 0xD8, 0xFF)) {
            return Formato.JPEG;
        }
        if (comecaCom(conteudo, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Formato.PNG;
        }
        if (comecaCom(conteudo, 0, 'R', 'I', 'F', 'F') && comecaCom(conteudo, 8, 'W', 'E', 'B', 'P')) {
            return Formato.WEBP;
        }
        throw new BusinessException(MENSAGEM_FORMATO);
    }

    private static boolean comecaCom(byte[] conteudo, int inicio, int... esperado) {
        if (conteudo.length < inicio + esperado.length) {
            return false;
        }
        for (int indice = 0; indice < esperado.length; indice++) {
            if ((conteudo[inicio + indice] & 0xFF) != esperado[indice]) {
                return false;
            }
        }
        return true;
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
