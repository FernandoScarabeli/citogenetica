package br.ufla.citogenetica.contagem.internal.domain;

/**
 * Uma marcação desenhada sobre a foto. Coordenadas de 0 a 1, relativas à largura e à altura da imagem,
 * para valer em qualquer tamanho de tela. Seta: de (x1, y1) até a ponta em (x2, y2). Texto: ancorado em (x1, y1).
 * {@code tamanho} multiplica o tamanho padrão do texto (nulo = 1); seta não usa.
 */
public record AnotacaoFoto(String tipo, double x1, double y1, Double x2, Double y2, String texto, String cor, Double tamanho) {
}
