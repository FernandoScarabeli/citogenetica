package br.ufla.citogenetica.shared.filter;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CriteriaCreatorCountQueryTest {

    @Test
    void especificacaoDeFiltroNaoDeveAdicionarOrdenacaoNaConsultaDeContagem() {
        var parametros = new LinkedMultiValueMap<String, String>();
        var camposPermitidos = new CamposPermitidos(java.util.Set.of("nome"), java.util.Set.of("nome"));
        var especificacao = CriteriaCreator.byFilterMap(parametros, camposPermitidos);

        @SuppressWarnings("unchecked")
        Root<Object> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        CriteriaQuery<Long> consultaContagem = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);

        when(cb.conjunction()).thenReturn(predicate);

        especificacao.toPredicate(root, consultaContagem, cb);

        verify(consultaContagem, never()).orderBy(any(Order.class));
    }
}
