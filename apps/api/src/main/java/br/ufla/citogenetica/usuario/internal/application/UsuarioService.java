package br.ufla.citogenetica.usuario.internal.application;

import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.usuario.api.error.ValidationConflictException;
import br.ufla.citogenetica.usuario.internal.mapper.UsuarioMapper;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.api.UsuarioPrincipal;
import br.ufla.citogenetica.usuario.internal.web.dto.request.AtualizarMeuPerfilRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.AtualizarSenhaRequestDTO;
import br.ufla.citogenetica.usuario.internal.web.dto.request.AtualizarUsuarioRequestDTO;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordPolicy passwordPolicy;


    @Transactional(readOnly = true)
    public Page<Usuario> buscarTodos(Specification<Usuario> spec, Pageable pageable) {
        return this.usuarioRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return this.usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Usuario.class, id));
    }

    @Transactional
    public Usuario atualizar(Long id, AtualizarUsuarioRequestDTO dto) {
        Usuario usuarioLogado = usuarioAutenticado();
        if (!usuarioLogado.getRole().getNome().equals(RolesEnum.ADMIN)) {
            throw new AccessDeniedException("Você não tem permissão para editar dados de outros usuários.");
        }

        Usuario entidade = this.usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Usuario.class, id));

        boolean isSelf = usuarioLogado.getId().equals(id);
        if (entidade.getRole().getNome().equals(RolesEnum.ADMIN) && !isSelf) {
            throw new ValidationConflictException("Não é permitido alterar os dados de um usuário ADMIN.");
        }

        entidade.setNome(dto.nome());
        return this.usuarioRepository.save(entidade);
    }

    @Transactional
    public Usuario atualizarMeuPerfil(AtualizarMeuPerfilRequestDTO dto) {
        Usuario usuarioLogado = usuarioAutenticado();
        Usuario entidade = this.usuarioRepository.findById(usuarioLogado.getId())
                .orElseThrow(() -> new NotFoundException(Usuario.class, usuarioLogado.getId()));

        entidade.setNome(dto.nome());
        return this.usuarioRepository.save(entidade);
    }

    @Transactional(readOnly = true)
    public Usuario buscarMeuPerfil() {
        Usuario usuarioLogado = usuarioAutenticado();
        return this.usuarioRepository.findById(usuarioLogado.getId())
                .orElseThrow(() -> new NotFoundException(Usuario.class, usuarioLogado.getId()));
    }

    @Transactional
    public void trocarStatus(Long id) {
        Usuario usuarioLogado = usuarioAutenticado();

        boolean isAdmin = usuarioLogado.getRole().getNome().equals(RolesEnum.ADMIN);

        if (usuarioLogado.getId().equals(id)) {
            throw new ValidationConflictException("Você não pode desativar o próprio usuário.");
        }

        if (!isAdmin) {
            throw new AccessDeniedException("Você não tem permissão para alterar o status de usuários.");
        }

        Usuario entidade = this.usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Usuario.class, id));

        StatusEnum novoStatus = (entidade.getStatus() == StatusEnum.ATIVO) ? StatusEnum.INATIVO : StatusEnum.ATIVO;
        entidade.setStatus(novoStatus);

        this.usuarioRepository.save(entidade);
        if (novoStatus == StatusEnum.INATIVO) {
            sessionRevocationService.revogarSessoes(entidade.getId(), null);
        }
    }

    @Transactional
    public void atualizarMinhaSenha(AtualizarSenhaRequestDTO dto, String sessionIdAtual) {
        Usuario usuarioLogado = usuarioAutenticado();
        Usuario entidade = this.usuarioRepository.findById(usuarioLogado.getId())
                .orElseThrow(() -> new NotFoundException(Usuario.class, usuarioLogado.getId()));

        if (!passwordEncoder.matches(dto.senhaAtual(), entidade.getPassword())) {
            throw new ValidationConflictException("A senha atual está incorreta.");
        }

        passwordPolicy.validate(dto.novaSenha());
        if (passwordEncoder.matches(dto.novaSenha(), entidade.getPassword())) {
            throw new ValidationConflictException("A nova senha deve ser diferente da atual.");
        }
        String encryptedPassword = this.passwordEncoder.encode(dto.novaSenha());
        entidade.setSenha(encryptedPassword);
        this.usuarioRepository.save(entidade);
        sessionRevocationService.revogarSessoes(entidade.getId(), sessionIdAtual);
    }

    private Usuario usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            throw new AccessDeniedException("É necessário estar autenticado.");
        }
        return this.usuarioRepository.findById(principal.id())
                .orElseThrow(() -> new AccessDeniedException("É necessário estar autenticado."));
    }
}
