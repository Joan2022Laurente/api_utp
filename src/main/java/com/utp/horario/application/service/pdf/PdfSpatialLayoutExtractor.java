package com.utp.horario.application.service.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extractor espacial de PDF que reconstruye líneas de layout conservando
 * coordenadas (x, y) de cada palabra para permitir reconstrucción de tablas multilínea.
 */
public class PdfSpatialLayoutExtractor extends PDFTextStripper {

    private final List<PdfLayoutLine> layoutLines = new ArrayList<>();
    private final List<TextPosition> currentWordChars = new ArrayList<>();
    private int currentPageNumber = 1;

    public PdfSpatialLayoutExtractor() throws IOException {
        super();
        setSortByPosition(true);
    }

    public static List<PdfLayoutLine> extractLines(PDDocument document) throws IOException {
        PdfSpatialLayoutExtractor extractor = new PdfSpatialLayoutExtractor();
        extractor.getText(document);
        return extractor.getSortedLayoutLines();
    }

    @Override
    protected void startPage(org.apache.pdfbox.pdmodel.PDPage page) throws IOException {
        this.currentPageNumber = getCurrentPageNo();
        super.startPage(page);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (textPositions == null || textPositions.isEmpty()) {
            return;
        }

        float baselineY = textPositions.get(0).getYDirAdj();
        PdfLayoutLine line = new PdfLayoutLine(currentPageNumber, baselineY);

        StringBuilder wordBuffer = new StringBuilder();
        float wordMinX = -1f;
        float wordMaxX = -1f;
        float wordY = baselineY;
        float wordHeight = textPositions.get(0).getHeightDir();

        for (TextPosition tp : textPositions) {
            String charStr = tp.getUnicode();
            if (charStr == null || charStr.isEmpty()) continue;

            if (Character.isWhitespace(charStr.charAt(0))) {
                if (wordBuffer.length() > 0) {
                    line.addWord(new PdfLayoutLine.PdfWord(wordBuffer.toString(), wordMinX, wordMaxX, wordY, wordHeight));
                    wordBuffer.setLength(0);
                    wordMinX = -1f;
                }
            } else {
                if (wordMinX < 0) {
                    wordMinX = tp.getXDirAdj();
                    wordY = tp.getYDirAdj();
                    wordHeight = tp.getHeightDir();
                }
                wordMaxX = tp.getXDirAdj() + tp.getWidthDirAdj();
                wordBuffer.append(charStr);
            }
        }

        if (wordBuffer.length() > 0) {
            line.addWord(new PdfLayoutLine.PdfWord(wordBuffer.toString(), wordMinX, wordMaxX, wordY, wordHeight));
        }

        if (!line.getWords().isEmpty()) {
            layoutLines.add(line);
        }

        super.writeString(text, textPositions);
    }

    public List<PdfLayoutLine> getSortedLayoutLines() {
        // Agrupar líneas muy cercanas en el mismo baseline (tolerancia epsilonY = 2.0pt)
        List<PdfLayoutLine> sorted = new ArrayList<>(layoutLines);
        sorted.sort(Comparator.comparingInt(PdfLayoutLine::getPage)
                .thenComparingDouble(PdfLayoutLine::getBaselineY)
                .thenComparingDouble(PdfLayoutLine::getMinX));
        return sorted;
    }
}
