package br.ufla.citogenetica.laboratorio.internal.application;

/** Esconde parte do endereço para mostrá-lo antes do login (ex.: {@code an***@ufla.br}). */
final class EmailMascarado {

    private static final int LETRAS_VISIVEIS = 2;

    private EmailMascarado() {
    }

    static String de(String email) {
        int arroba = email.lastIndexOf('@');
        if (arroba < 1) {
            return "***";
        }
        String usuario = email.substring(0, arroba);
        // Endereço curto mostra só a primeira letra, para não revelar quase tudo.
        int visiveis = usuario.length() > LETRAS_VISIVEIS ? LETRAS_VISIVEIS : 1;
        return usuario.substring(0, visiveis) + "***" + email.substring(arroba);
    }
}
