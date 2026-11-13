package br.ufla.citogenetica.shared.storage;

/** Porta compartilhada para guardar as fotos das lâminas fora do banco. */
public interface FotoLaminaStorage {

    /** Grava uma imagem já validada e devolve a chave nova; cada foto ganha uma chave própria. */
    String salvarFotoLamina(byte[] conteudo, FormatoImagem formato);

    byte[] lerFotoLamina(String chave);

    void apagarFotoLamina(String chave);
}
