package br.ufla.citogenetica.shared.email;

/** Porta compartilhada para envio de mensagens de e-mail. */
public interface EmailSender {

    void sendEmail(String toEmail, String emailSubject, String emailBody);
}
