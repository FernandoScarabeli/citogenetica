package br.ufla.citogenetica.shared.storage;

import br.ufla.citogenetica.shared.web.error.BusinessException;

import java.util.Arrays;

/**
 * Formatos de imagem aceitos no sistema. O formato vem dos primeiros bytes do arquivo,
 * nunca do nome ou do tipo informado pelo navegador.
 */
public enum FormatoImagem {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    private final String extensao;
    private final String contentType;

    FormatoImagem(String extensao, String contentType) {
        this.extensao = extensao;
        this.contentType = contentType;
    }

    public String extensao() {
        return extensao;
    }

    public String contentType() {
        return contentType;
    }

    /** Detecta o formato e confere o tamanho; recusa com a mensagem informada. */
    public static FormatoImagem detectar(byte[] conteudo, int tamanhoMaximoBytes, String mensagemRecusa) {
        if (conteudo == null || conteudo.length == 0 || conteudo.length > tamanhoMaximoBytes) {
            throw new BusinessException(mensagemRecusa);
        }
        if (comecaCom(conteudo, 0, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (comecaCom(conteudo, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (comecaCom(conteudo, 0, 'R', 'I', 'F', 'F') && comecaCom(conteudo, 8, 'W', 'E', 'B', 'P')) {
            return WEBP;
        }
        throw new BusinessException(mensagemRecusa);
    }

    /** Formato de uma chave já gravada (pela extensão que o próprio sistema escolheu). */
    public static FormatoImagem daChave(String chave) {
        String extensao = chave.substring(chave.lastIndexOf('.') + 1);
        return Arrays.stream(values())
                .filter(formato -> formato.extensao.equals(extensao))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Extensão de imagem desconhecida: " + extensao));
    }

    private static boolean comecaCom(byte[] conteudo, int inicio, int... esperado) {
        if (conteudo.length < inicio + esperado.length) {
            return false;
        }
        for (int indice = 0; indice < esperado.length; indice++) {
            if ((conteudo[inicio + indice] & 0xFF) != esperado[indice]) {
                return false;
            }
        }
        return true;
    }
}
