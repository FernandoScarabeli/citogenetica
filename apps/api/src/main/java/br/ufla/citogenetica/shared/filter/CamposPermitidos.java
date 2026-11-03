package br.ufla.citogenetica.shared.filter;

import java.util.Set;

/**
 * Lista explícita dos campos que uma listagem aceita para filtro e ordenação.
 * Campo fora da lista responde 400: atributos internos (como o hash da senha) nunca
 * viram critério de busca, mesmo que existam na entidade.
 */
public record CamposPermitidos(Set<String> filtro, Set<String> ordenacao) {

    public CamposPermitidos {
        filtro = Set.copyOf(filtro);
        ordenacao = Set.copyOf(ordenacao);
    }
}
