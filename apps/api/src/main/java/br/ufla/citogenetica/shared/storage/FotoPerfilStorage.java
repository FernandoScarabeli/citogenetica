package br.ufla.citogenetica.shared.storage;

/** Porta compartilhada para guardar as fotos de perfil fora do banco. */
public interface FotoPerfilStorage {

    /** Grava uma imagem já validada e devolve a chave nova; cada foto ganha uma chave própria. */
    String salvarFotoPerfil(byte[] conteudo, String extensao, String contentType);

    byte[] lerFotoPerfil(String chave);

    void apagarFotoPerfil(String chave);
}
