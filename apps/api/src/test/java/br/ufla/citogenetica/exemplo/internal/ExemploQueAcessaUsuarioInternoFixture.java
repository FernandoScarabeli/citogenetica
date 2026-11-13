package br.ufla.citogenetica.exemplo.internal;

import br.ufla.citogenetica.usuario.internal.UsuarioInternoFixture;

/** Fixture usada para provar que a regra arquitetural rejeita acesso a um interno alheio. */
public class ExemploQueAcessaUsuarioInternoFixture {

    private final UsuarioInternoFixture usuario;

    public ExemploQueAcessaUsuarioInternoFixture(UsuarioInternoFixture usuario) {
        this.usuario = usuario;
    }

    public UsuarioInternoFixture usuario() {
        return usuario;
    }
}
