package br.ufla.citogenetica.contagem.internal.web.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoLaminas.LaminaExportada;
import br.ufla.citogenetica.contagem.internal.application.ExportacaoResumos.LaminaResumida;
import br.ufla.citogenetica.contagem.internal.application.ResumoLamina;
import br.ufla.citogenetica.contagem.internal.web.Percentual;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfDate;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Relatório PDF (A4 retrato) de uma lâmina, com os números do resumo oficial. Usa a Helvetica padrão (cp1252, que
 * cobre o português); o que a fonte não cobre vira "?" em vez de derrubar a geração.
 */
public final class RelatorioLaminaPdf {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String TRACO = "—";
    private static final String NOTA = "Uma célula pode ter mais de um tipo de anormalidade; por isso os percentuais "
            + "dos tipos podem somar mais que o % de anormais.";
    private static final Charset CP1252 = Charset.forName("windows-1252");

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font NEGRITO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font CELULA = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font CELULA_NEGRITO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

    private RelatorioLaminaPdf() {
    }

    public static byte[] gerar(LaminaResumida item, Instant emitidoEm) {
        LaminaExportada lamina = item.lamina();
        ResumoLamina resumo = item.resumo();
        String codigo = lamina.protocolo().codigo();
        boolean mitose = "mitose".equals(codigo);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 40, 40, 40, 56);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, saida);
            escritor.setPageEvent(new NumeroDePagina());
            documento.addTitle(texto(lamina.nome()));
            documento.open();
            // Metadados neutros e estáveis: produtor fixo e data de criação igual à da emissão.
            escritor.getInfo().put(PdfName.PRODUCER, new PdfString("Citogenética"));
            GregorianCalendar emissao = GregorianCalendar.from(emitidoEm.atZone(ExportacaoLaminas.FUSO_ARQUIVOS));
            emissao.setTimeZone(TimeZone.getTimeZone(ExportacaoLaminas.FUSO_ARQUIVOS));
            escritor.getInfo().put(PdfName.CREATIONDATE, new PdfDate(emissao));

            Paragraph titulo = new Paragraph(titulo(codigo), TITULO);
            titulo.setSpacingAfter(10);
            documento.add(titulo);
            cabecalho(documento, lamina, resumo, emitidoEm);
            documento.add(tabela(resumo));
            if (mitose) {
                Paragraph indice = new Paragraph("Índice mitótico: " + Percentual.formatar(resumo.indiceMitotico()) + "%",
                        NEGRITO);
                indice.setSpacingBefore(8);
                documento.add(indice);
            }
            Paragraph nota = new Paragraph(NOTA, PEQUENA);
            nota.setSpacingBefore(6);
            documento.add(nota);
            if (lamina.observacoes() != null && !lamina.observacoes().isBlank()) {
                Paragraph rotulo = new Paragraph("Observações", NEGRITO);
                rotulo.setSpacingBefore(12);
                documento.add(rotulo);
                documento.add(new Paragraph(observacoes(lamina.observacoes()), NORMAL));
            }
            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o PDF da lâmina.", e);
        }
        return saida.toByteArray();
    }

    private static String titulo(String codigoProtocolo) {
        return switch (codigoProtocolo) {
            case "meiose" -> "Registro de Análise Meiótica";
            case "mitose" -> "Registro de Ciclo Celular e Índice Mitótico";
            default -> "Relatório da lâmina";
        };
    }

    private static void cabecalho(Document documento, LaminaExportada lamina, ResumoLamina resumo, Instant emitidoEm)
            throws DocumentException {
        linha(documento, "Lâmina", texto(lamina.nome()));
        linha(documento, "Espécie", texto(lamina.especie()));
        linha(documento, "Tratamento", lamina.tratamento() == null || lamina.tratamento().isBlank()
                ? TRACO : texto(lamina.tratamento()));
        linha(documento, "Responsável", texto(lamina.dono().nome()));
        linha(documento, "Protocolo", texto(lamina.protocolo().nome()) + ", versão " + lamina.protocolo().versao());
        linha(documento, "Total de células", Long.toString(resumo.total()));
        if (lamina.meta() != null) {
            linha(documento, "Meta", resumo.total() + " de " + lamina.meta() + " células");
        }
        linha(documento, "Data da emissão", DATA_HORA.format(emitidoEm.atZone(ExportacaoLaminas.FUSO_ARQUIVOS)));
    }

    private static void linha(Document documento, String rotulo, String valor) throws DocumentException {
        Phrase frase = new Phrase();
        frase.add(new Chunk(rotulo + ": ", NEGRITO));
        frase.add(new Chunk(valor, NORMAL));
        Paragraph paragrafo = new Paragraph(frase);
        paragrafo.setSpacingAfter(2);
        documento.add(paragrafo);
    }

    private static PdfPTable tabela(ResumoLamina resumo) {
        PdfPTable tabela = new PdfPTable(new float[] {2.2f, 1.2f, 1.2f, 1f, 1.3f, 4.2f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(12);
        tabela.setHeaderRows(1);
        for (String titulo : new String[] {"Fase", "Normais", "Anormais", "Total", "% anormais", "Tipos de anormalidade"}) {
            PdfPCell cabecalho = celula(titulo, CELULA_NEGRITO, titulo.equals("Fase") || titulo.startsWith("Tipos")
                    ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            cabecalho.setBackgroundColor(new Color(0xE6, 0xE6, 0xE6));
            tabela.addCell(cabecalho);
        }
        for (ResumoLamina.Fase fase : resumo.fases()) {
            String tipos = fase.tipos().stream()
                    .map(tipo -> texto(tipo.nome()) + ": " + tipo.contagem() + " (" + Percentual.formatar(tipo.percentual()) + "%)")
                    .collect(Collectors.joining("\n"));
            linhaDaTabela(tabela, CELULA, texto(fase.nome()), fase.normais(), fase.anormais(), fase.total(),
                    fase.percentualAnormais(), tipos);
        }
        double percentualTotal = resumo.total() == 0 ? 0 : resumo.anormais() * 100.0 / resumo.total();
        linhaDaTabela(tabela, CELULA_NEGRITO, "Total", resumo.total() - resumo.anormais(), resumo.anormais(),
                resumo.total(), percentualTotal, "");
        return tabela;
    }

    private static void linhaDaTabela(PdfPTable tabela, Font fonte, String fase, long normais, long anormais,
                                      long total, double percentual, String tipos) {
        tabela.addCell(celula(fase, fonte, Element.ALIGN_LEFT));
        tabela.addCell(celula(Long.toString(normais), fonte, Element.ALIGN_RIGHT));
        tabela.addCell(celula(Long.toString(anormais), fonte, Element.ALIGN_RIGHT));
        tabela.addCell(celula(Long.toString(total), fonte, Element.ALIGN_RIGHT));
        tabela.addCell(celula(Percentual.formatar(percentual) + "%", fonte, Element.ALIGN_RIGHT));
        tabela.addCell(celula(tipos, fonte, Element.ALIGN_LEFT));
    }

    private static PdfPCell celula(String conteudo, Font fonte, int alinhamento) {
        PdfPCell celula = new PdfPCell(new Phrase(conteudo, fonte));
        celula.setHorizontalAlignment(alinhamento);
        celula.setPadding(4);
        return celula;
    }

    /** Texto de uma linha: controles viram espaço e o que o cp1252 não cobre vira "?". */
    private static String texto(String original) {
        if (original == null) {
            return "";
        }
        return coberto(original.replaceAll("[\\p{Cc}\\p{Cf}]", " ").strip());
    }

    /** Observações: igual a {@link #texto}, mas preserva as quebras de linha. */
    private static String observacoes(String original) {
        return coberto(original.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[\\p{Cc}&&[^\n]]|\\p{Cf}", " "));
    }

    private static String coberto(String texto) {
        CharsetEncoder codificador = CP1252.newEncoder();
        StringBuilder resultado = new StringBuilder();
        texto.codePoints().forEach(ponto -> {
            String simbolo = new String(Character.toChars(ponto));
            resultado.append(codificador.canEncode(simbolo) ? simbolo : "?");
        });
        return resultado.toString();
    }

    /** Rodapé "Página X de Y"; o total só é conhecido ao fechar o documento, por isso o modelo (template). */
    private static final class NumeroDePagina extends PdfPageEventHelper {

        private PdfTemplate total;
        private BaseFont fonte;

        @Override
        public void onOpenDocument(PdfWriter escritor, Document documento) {
            try {
                fonte = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException("Fonte padrão indisponível.", e);
            }
            total = escritor.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            PdfContentByte conteudo = escritor.getDirectContent();
            String prefixo = "Página " + escritor.getPageNumber() + " de ";
            float largura = fonte.getWidthPoint(prefixo, 8);
            float x = (documento.getPageSize().getWidth() - largura - 12) / 2;
            float y = 28;
            conteudo.beginText();
            conteudo.setFontAndSize(fonte, 8);
            conteudo.setTextMatrix(x, y);
            conteudo.showText(prefixo);
            conteudo.endText();
            conteudo.addTemplate(total, x + largura, y);
        }

        @Override
        public void onCloseDocument(PdfWriter escritor, Document documento) {
            total.beginText();
            total.setFontAndSize(fonte, 8);
            total.setTextMatrix(0, 0);
            total.showText(Integer.toString(escritor.getPageNumber() - 1));
            total.endText();
        }
    }
}
