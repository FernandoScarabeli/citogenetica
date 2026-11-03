package br.ufla.citogenetica.laboratorio.internal.application;

import java.util.List;

final class ConviteEmailTemplate {

    private ConviteEmailTemplate() {
    }

    static String criarParaLaboratorio(String nomeLaboratorio, List<String> papeis, String link, int validadeDias) {
        return pagina("Você recebeu um convite",
                "Você foi convidado para participar do laboratório <strong>" + escape(nomeLaboratorio) + "</strong>.",
                "Papéis após aceitar: " + escape(String.join(", ", papeis)) + ".",
                link, validadeDias);
    }

    static String criarParaOrientacao(String nomeLaboratorio, String link, int validadeDias) {
        return pagina("Convite para orientação",
                "Você foi convidado para ser orientado no laboratório <strong>" + escape(nomeLaboratorio) + "</strong>.",
                "Ao aceitar, seu vínculo de orientação será registrado. Se ainda não participar do laboratório, você também será incluído como membro.",
                link, validadeDias);
    }

    private static String pagina(String titulo, String descricao, String complemento, String link, int validadeDias) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><title>%s</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f0eded; padding: 24px;">
                  <main style="max-width: 600px; margin: auto; background: #fff; padding: 32px;">
                    <h1 style="color: #00A859;">Citogenética UFLA</h1>
                    <h2>%s</h2>
                    <p>%s</p>
                    <p>%s</p>
                    <p>Entre ou crie sua conta, confirme este endereço de e-mail e abra o link:</p>
                    <p><a href="%s">Abrir convite</a></p>
                    <p>O link expira em %d dias e pode ser usado uma única vez.</p>
                    <p>Se você não esperava este convite, ignore esta mensagem.</p>
                  </main>
                </body>
                </html>
                """.formatted(escape(titulo), escape(titulo), descricao, complemento,
                escape(link), validadeDias);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
