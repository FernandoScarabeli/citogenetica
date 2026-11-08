package br.ufla.citogenetica.contagem.internal.application;

import java.util.regex.Pattern;

import br.ufla.citogenetica.shared.web.error.BadRequestException;

/**
 * Regras únicas dos textos da lâmina; o resultado é o que vai para o banco e o que se compara na idempotência.
 * <ul>
 *   <li>nome: obrigatório, até 120 caracteres depois de colapsar espaços;</li>
 *   <li>espécie e tratamento: opcionais, até 120; ausente ou vazio vira {@code ""};</li>
 *   <li>observações: opcionais, até 5000, mantêm as quebras de linha.</li>
 * </ul>
 * Qualquer caractere da categoria Unicode C (controle, formato, não atribuído...) é recusado, com a única
 * exceção de {@code \n}, {@code \r} e {@code \t} nas observações. Assim o {@code \u0000}, que o PostgreSQL
 * não armazena, nunca chega ao banco. Os limites contam pontos de código, como o {@code CHAR_LENGTH} do banco.
 */
public final class TextoLamina {

    public static final int LIMITE_CURTO = 120;
    public static final int LIMITE_OBSERVACOES = 5000;

    private static final Pattern CONTROLE_EXCETO_QUEBRAS_E_TAB = Pattern.compile("[\\p{C}&&[^\\n\\r\\t]]");

    private TextoLamina() {
    }

    public static String nome(String bruto) {
        String exibido = bruto == null ? "" : NomeTipoAnormalidade.exibicao(bruto);
        if (exibido.isEmpty()) {
            throw new BadRequestException("Informe o nome da lâmina.");
        }
        return validar(exibido, "O nome da lâmina", LIMITE_CURTO);
    }

    public static String especie(String bruto) {
        return curtoOpcional(bruto, "A espécie");
    }

    public static String tratamento(String bruto) {
        return curtoOpcional(bruto, "O tratamento");
    }

    public static String observacoes(String bruto) {
        if (bruto == null) {
            return "";
        }
        String texto = bruto.strip();
        if (CONTROLE_EXCETO_QUEBRAS_E_TAB.matcher(texto).find()) {
            throw new BadRequestException("As observações não podem conter caracteres de controle ou invisíveis.");
        }
        if (texto.codePointCount(0, texto.length()) > LIMITE_OBSERVACOES) {
            throw new BadRequestException("As observações devem ter no máximo " + LIMITE_OBSERVACOES + " caracteres.");
        }
        return texto;
    }

    private static String curtoOpcional(String bruto, String campo) {
        if (bruto == null) {
            return "";
        }
        return validar(NomeTipoAnormalidade.exibicao(bruto), campo, LIMITE_CURTO);
    }

    private static String validar(String exibido, String campo, int limite) {
        if (NomeTipoAnormalidade.contemCaractereInvisivelOuDeControle(exibido)) {
            throw new BadRequestException(campo + " não pode conter caracteres de controle ou invisíveis.");
        }
        if (exibido.codePointCount(0, exibido.length()) > limite) {
            throw new BadRequestException(campo + " deve ter no máximo " + limite + " caracteres.");
        }
        return exibido;
    }
}
