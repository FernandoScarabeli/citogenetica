package br.ufla.citogenetica.service;

import br.ufla.citogenetica.configuration.email.EmailTemplates;
import br.ufla.citogenetica.configuration.security.JWTConfig;
import br.ufla.citogenetica.exception.BusinessException;
import br.ufla.citogenetica.exception.NotFoundException;
import br.ufla.citogenetica.exception.ValidationConflictException;
import br.ufla.citogenetica.mapper.UsuarioMapper;
import br.ufla.citogenetica.model.roles.Role;
import br.ufla.citogenetica.model.type.RolesEnum;
import br.ufla.citogenetica.model.type.StatusEnum;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.model.usuario.dto.request.LoginRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.RedefinirSenhaRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.UsuarioRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.request.ValidarCodigoRequestDTO;
import br.ufla.citogenetica.model.usuario.dto.response.LoginResponseDTO;
import br.ufla.citogenetica.model.usuario.dto.response.UsuarioResponseDTO;
import br.ufla.citogenetica.repository.RoleRepository;
import br.ufla.citogenetica.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTConfig tokenService;
    private final UsuarioMapper usuarioMapper;
    private final EmailService emailService;

    @Value("${api.security.token.recovery-expiration-minutes}")
    private long expirationMinutes;

    @Value("${api.frontend.url}")
    private String frontendUrl;

    public LoginResponseDTO login(LoginRequestDTO requestDTO) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(requestDTO.email(), requestDTO.senha());
        var auth = this.authenticationManager.authenticate(usernamePassword);
        var token = this.tokenService.generateToken((Usuario) auth.getPrincipal());
        return new LoginResponseDTO(token);
    }


    @Transactional
    public UsuarioResponseDTO register(UsuarioRequestDTO requestDTO) {

        if (this.usuarioRepository.existsByEmail(requestDTO.email())) {
            throw new ValidationConflictException("E-mail já cadastrado.");
        }

        Usuario usuario = this.usuarioMapper.requestToEntity(requestDTO);

        String roleSolicitada = requestDTO.roleName().trim().toUpperCase();
        RolesEnum roleEnum;

        try{
            roleEnum = RolesEnum.valueOf(roleSolicitada);
        } catch (IllegalArgumentException e){
            throw new BusinessException("Role inválida: " + roleSolicitada);
        }

        if (roleEnum != RolesEnum.USER) {
            throw new BusinessException("Não é permitido registrar um usuário com a role: " + roleSolicitada);
        }

        Role role = this.roleRepository.findByNome(roleEnum)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Erro interno: Role " + roleEnum.name() + " não encontrada."));
        usuario.setRole(role);

        String encryptedPassword = this.passwordEncoder.encode(requestDTO.senha());
        usuario.setSenha(encryptedPassword);
        usuario.setStatus(StatusEnum.ATIVO);

        this.usuarioRepository.save(usuario);
        return this.usuarioMapper.toResponseDTO(usuario);
    }

    @Transactional
    public void recuperarSenha(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (usuario.getStatus() == StatusEnum.INATIVO) {
            throw new ValidationConflictException("Usuário inativo");
        }

        String codigo = gerarCodigoNumerico();

        usuario.setTokenRecuperacaoSenha(codigo);
        usuario.setDataExpiracaoCodigo(LocalDateTime.now().plusMinutes(15));

        usuarioRepository.save(usuario);

        emailService.sendEmail(
                usuario.getEmail(),
                "Seu código de recuperação de senha",
                EmailTemplates.templateRecuperacaoSenha(codigo));
    }

    @Transactional
    public void redefinirSenha(RedefinirSenhaRequestDTO requestDTO) {
        Usuario usuario = usuarioRepository.findByEmail(requestDTO.email())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (usuario.getTokenRecuperacaoSenha() == null || usuario.getDataExpiracaoCodigo() == null) {
            throw new ValidationConflictException("Nenhuma solicitação de recuperação encontrada");
        }

        if (!usuario.getTokenRecuperacaoSenha().equals(requestDTO.codigo())) {
            throw new ValidationConflictException("Código inválido");
        }

        if (LocalDateTime.now().isAfter(usuario.getDataExpiracaoCodigo())) {
            throw new ValidationConflictException("O código expirou. Solicite um novo.");
        }

        if (passwordEncoder.matches(requestDTO.novaSenha(), usuario.getPassword())) {
            throw new ValidationConflictException("A nova senha deve ser diferente da atual");
        }

        usuario.setSenha(passwordEncoder.encode(requestDTO.novaSenha()));
        usuario.setTokenRecuperacaoSenha(null);
        usuario.setDataExpiracaoCodigo(null);

        usuarioRepository.save(usuario);
    }

    private String gerarCodigoNumerico() {
        Random random = new Random();
        int numero = 100000 + random.nextInt(900000);
        return String.valueOf(numero);
    }

    public void validarCodigo(ValidarCodigoRequestDTO requestDTO) {
        Usuario usuario = usuarioRepository.findByEmail(requestDTO.email())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (usuario.getTokenRecuperacaoSenha() == null || usuario.getDataExpiracaoCodigo() == null) {
            throw new ValidationConflictException("Nenhuma solicitação de recuperação encontrada");
        }

        if (!usuario.getTokenRecuperacaoSenha().equals(requestDTO.codigo())) {
            throw new ValidationConflictException("Código inválido");
        }

        if (LocalDateTime.now().isAfter(usuario.getDataExpiracaoCodigo())) {
            throw new ValidationConflictException("O código expirou. Solicite um novo.");
        }
    }
}
