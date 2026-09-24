package com.utp.horario.application.service.pdf;

import java.util.ArrayList;
import java.util.List;

public class PdfLayoutLine {
    private final int page;
    private final float baselineY;
    private final List<PdfWord> words;

    public PdfLayoutLine(int page, float baselineY) {
        this.page = page;
        this.baselineY = baselineY;
        this.words = new ArrayList<>();
    }

    public void addWord(PdfWord word) {
        this.words.add(word);
    }

    public int getPage() {
        return page;
    }

    public float getBaselineY() {
        return baselineY;
    }

    public List<PdfWord> getWords() {
        return words;
    }

    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            sb.append(words.get(i).text());
            if (i < words.size() - 1) sb.append(" ");
        }
        return sb.toString().trim();
    }

    public float getMinX() {
        return words.isEmpty() ? 0f : words.get(0).minX();
    }

    public float getMaxX() {
        return words.isEmpty() ? 0f : words.get(words.size() - 1).maxX();
    }

    public record PdfWord(String text, float minX, float maxX, float y, float height) {}
}
