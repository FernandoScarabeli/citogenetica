package br.ufla.citogenetica.laboratorio.internal.application;

import java.util.List;

final class ConviteLaboratorioEmailTemplate {

    private ConviteLaboratorioEmailTemplate() {
    }

    static String criar(String nomeLaboratorio, List<String> papeis, String link, int validadeDias) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><title>Convite para laboratório</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f0eded; padding: 24px;">
                  <main style="max-width: 600px; margin: auto; background: #fff; padding: 32px;">
                    <h1 style="color: #00A859;">Citogenética UFLA</h1>
                    <h2>Você recebeu um convite</h2>
                    <p>Você foi convidado para participar do laboratório <strong>%s</strong>.</p>
                    <p>Papéis após aceitar: %s.</p>
                    <p>Para aceitar, entre ou crie sua conta, confirme este endereço de e-mail e abra o link:</p>
                    <p><a href="%s">Abrir convite</a></p>
                    <p>O link expira em %d dias e pode ser usado uma única vez.</p>
                    <p>Se você não esperava este convite, ignore esta mensagem.</p>
                  </main>
                </body>
                </html>
                """.formatted(escape(nomeLaboratorio), escape(String.join(", ", papeis)),
                escape(link), validadeDias);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
