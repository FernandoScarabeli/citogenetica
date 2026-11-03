package br.ufla.citogenetica.usuario.internal.application;

public class EmailTemplates {

    public static String templateConfirmacaoEmail(String confirmationUrl, long expirationHours) {
        String safeUrl = escapeHtmlAttribute(confirmationUrl);
        return String.format(
                """
                        <!DOCTYPE html>
                        <html lang="pt-BR">
                        <head><meta charset="UTF-8"><title>Confirme seu e-mail</title></head>
                        <body style="font-family: Arial, sans-serif; background-color: #f0eded; padding: 24px;">
                          <main style="max-width: 600px; margin: auto; background: #fff; padding: 32px;">
                            <h1 style="color: #00A859;">Citogenética UFLA</h1>
                            <h2>Confirme seu e-mail</h2>
                            <p>Para concluir seu cadastro, confirme que este endereço de e-mail é seu.</p>
                            <p><a href="%s">Confirmar meu e-mail</a></p>
                            <p>Este link expira em %d horas e só pode ser usado uma vez.</p>
                            <p>Se você não criou esta conta, ignore esta mensagem.</p>
                          </main>
                        </body>
                        </html>
                        """, safeUrl, expirationHours);
    }

    public static String templateConfirmacaoTrocaEmail(String confirmationUrl, long expirationHours) {
        String safeUrl = escapeHtmlAttribute(confirmationUrl);
        return String.format(
                """
                        <!DOCTYPE html>
                        <html lang="pt-BR">
                        <head><meta charset="UTF-8"><title>Confirme a troca de e-mail</title></head>
                        <body style="font-family: Arial, sans-serif; background-color: #f0eded; padding: 24px;">
                          <main style="max-width: 600px; margin: auto; background: #fff; padding: 32px;">
                            <h1 style="color: #00A859;">Citogenética UFLA</h1>
                            <h2>Confirme a troca de e-mail</h2>
                            <p>Recebemos uma solicitação para usar este endereço na sua conta.</p>
                            <p><a href="%s">Confirmar novo e-mail</a></p>
                            <p>Seu e-mail atual continuará valendo até a confirmação. Este link expira em %d horas e só pode ser usado uma vez.</p>
                            <p>Se você não solicitou a troca, ignore esta mensagem.</p>
                          </main>
                        </body>
                        </html>
                        """, safeUrl, expirationHours);
    }

    public static String templateAvisoTrocaEmail() {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><title>Aviso de alteração de e-mail</title></head>
                <body style="font-family: Arial, sans-serif; background-color: #f0eded; padding: 24px;">
                  <main style="max-width: 600px; margin: auto; background: #fff; padding: 32px;">
                    <h1 style="color: #00A859;">Citogenética UFLA</h1>
                    <h2>Aviso de alteração de e-mail</h2>
                    <p>O endereço de e-mail da sua conta foi alterado.</p>
                    <p>Se você não solicitou essa alteração, procure o suporte do projeto para proteger sua conta.</p>
                    <p>Esta mensagem é apenas um aviso e não contém link de ação.</p>
                  </main>
                </body>
                </html>
                """;
    }

    public static String templateRecuperacaoSenha(String codigo, long expirationMinutes) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>
                                body { font-family: Arial, sans-serif; }
                            </style>
                        </head>
                        <body style="margin: 0; padding: 0; background-color: #f0eded;">
                            <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 600px; margin: 20px auto;">
                                <tr>
                                    <td align="center" style="background-color: #00A859; padding: 20px 0;">
                                        <h1 style="color: white;">Citogenética UFLA</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #ffffff; padding: 40px 30px;">
                                        <h2 style="color: #1a1a1a; text-align: center;">Código de Recuperação</h2>
                                        <p style="color: #555555; text-align: center;">
                                            Recebemos uma solicitação para redefinir a sua senha.
                                            Use o código abaixo para prosseguir:
                                        </p>
                                        <div style="text-align: center; margin: 30px 0;">
                                            <span style="background-color: #f0eded; color: #1a1a1a; padding: 15px 30px;
                                               font-size: 24px; letter-spacing: 5px; font-weight: bold; border-radius: 5px; border: 1px solid #ccc;">
                                                %s
                                            </span>
                                        </div>
                                        <p style="color: #555555; text-align: center; font-size: 14px;">
                                            Este código expira em %d minutos.
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 20px; text-align: center; color: #888888; font-size: 12px;">
                                        Sistema de Registro de Lâminas — UFLA.
                                    </td>
                                </tr>
                            </table>
                        </body>
                        </html>
                        """,
                codigo,
                expirationMinutes);
    }

    private static String escapeHtmlAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;");
    }

}
