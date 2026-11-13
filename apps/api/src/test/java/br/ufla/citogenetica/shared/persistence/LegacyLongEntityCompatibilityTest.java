package br.ufla.citogenetica.shared.persistence;

import java.lang.reflect.ParameterizedType;

import br.ufla.citogenetica.exemplo.internal.domain.Exemplo;
import br.ufla.citogenetica.notificacao.internal.domain.Notificacao;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyLongEntityCompatibilityTest {

    @Test
    void entidadesExistentesContinuamNaBaseLong() {
        assertUsaLong(Usuario.class);
        assertUsaLong(Notificacao.class);
        assertUsaLong(Exemplo.class);
    }

    private void assertUsaLong(Class<?> entidade) {
        var superclasse = (ParameterizedType) entidade.getGenericSuperclass();
        assertEquals(BaseEntity.class, superclasse.getRawType());
        assertEquals(Long.class, superclasse.getActualTypeArguments()[0]);
    }
}
