package br.ufla.citogenetica.service;

import br.ufla.citogenetica.exception.NotFoundException;
import br.ufla.citogenetica.exception.ValidationConflictException;
import br.ufla.citogenetica.mapper.UsuarioMapper;
import br.ufla.citogenetica.model.type.RolesEnum;
import br.ufla.citogenetica.model.type.StatusEnum;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.model.usuario.dto.request.AtualizarSenhaRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.AtualizarUsuarioRequestDTO;
import br.ufla.citogenetica.repository.UsuarioRepository;
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

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuarioLogado = (Usuario) authentication.getPrincipal();

        boolean isAdmin = usuarioLogado.getRole().getNome().equals(RolesEnum.ADMIN);
        boolean isSelf = usuarioLogado.getId().equals(id);

        if (!isAdmin && !isSelf) {
            throw new AccessDeniedException("Você não tem permissão para editar os dados de outro usuário.");
        }

        Usuario entidade = this.usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Usuario.class, id));

        if (entidade.getRole().getNome().equals(RolesEnum.ADMIN) && !isSelf) {
            throw new ValidationConflictException("Não é permitido alterar os dados de um usuário ADMIN.");
        }

        entidade.setNome(dto.nome());

        if (!entidade.getEmail().equals(dto.email())) {
            if (!isAdmin) {
                throw new AccessDeniedException("Apenas administradores podem alterar o endereço de e-mail.");
            }

            if (this.usuarioRepository.existsByEmail(dto.email())) {
                throw new ValidationConflictException("Já existe um usuário cadastrado com este e-mail.");
            }

            entidade.setEmail(dto.email());
        }

        return this.usuarioRepository.save(entidade);
    }

    @Transactional
    public void trocarStatus(Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuarioLogado = (Usuario) authentication.getPrincipal();

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
    }

    @Transactional
    public Usuario atualizarSenha(Long id, AtualizarSenhaRequestDTO dto) {
        Usuario entidade = this.usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Usuario.class, id));

        if (!passwordEncoder.matches(dto.senhaAtual(), entidade.getPassword())) {
            throw new ValidationConflictException("A senha atual está incorreta.");
        }

        String encryptedPassword = this.passwordEncoder.encode(dto.novaSenha());
        entidade.setSenha(encryptedPassword);
        return this.usuarioRepository.save(entidade);
    }
}
