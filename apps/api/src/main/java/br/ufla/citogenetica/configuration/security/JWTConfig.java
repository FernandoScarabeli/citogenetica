package br.ufla.citogenetica.configuration.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import br.ufla.citogenetica.exception.ModelException;
import br.ufla.citogenetica.model.usuario.Usuario;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JWTConfig {

    private final JwtProperties properties;

    public String generateToken(Usuario usuario) {
        try{
            Algorithm algorithm = Algorithm.HMAC256(properties.secret().reveal());
            String token = JWT.create()
                    .withIssuer("auth-api")
                    .withSubject(usuario.getEmail())
                    .withExpiresAt(genExpirationDate())
                    .sign(algorithm);
            return token;
        } catch (JWTCreationException exception){
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    public String validateToken(String token ) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(properties.secret().reveal());
            return JWT.require(algorithm)
                    .withIssuer("auth-api")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            throw new ModelException ("Erro ao validar token");
        }
    }

    public Optional<String> getSubjectFromToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(properties.secret().reveal());
            String subject = JWT.require(algorithm)
                    .withIssuer("auth-api")
                    .build()
                    .verify(token)
                    .getSubject();
            return Optional.ofNullable(subject);
        } catch (JWTVerificationException exception) {
            return Optional.empty();
        }
    }

    private Instant genExpirationDate(){
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }
}
