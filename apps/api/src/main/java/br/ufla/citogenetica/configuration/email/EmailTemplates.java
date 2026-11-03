package br.ufla.citogenetica.configuration.email;

public class EmailTemplates {

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

}
