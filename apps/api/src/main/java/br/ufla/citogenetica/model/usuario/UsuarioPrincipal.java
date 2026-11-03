package br.ufla.citogenetica.model.usuario;

import br.ufla.citogenetica.model.type.RolesEnum;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/** Principal serializável, sem entidade JPA nem hash de senha dentro da sessão. */
public record UsuarioPrincipal(Long id, String email, String nome, RolesEnum role)
        implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static UsuarioPrincipal de(Usuario usuario) {
        return new UsuarioPrincipal(usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getRole().getNome());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public String chaveSessao() {
        return id.toString();
    }
}
