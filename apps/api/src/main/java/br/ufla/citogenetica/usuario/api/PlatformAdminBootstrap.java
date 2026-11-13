package br.ufla.citogenetica.usuario.api;

/** Operação administrativa de uso único para criar o primeiro administrador global. */
public interface PlatformAdminBootstrap {

    long promoteFirstAdmin(String email);
}
