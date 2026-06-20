package org.example.config;

import org.example.domain.SensitiveWord;
import org.example.repo.SensitiveWordRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SensitiveWordFilterService {
    private final SensitiveWordRepository sensitiveWordRepository;

    public SensitiveWordFilterService(SensitiveWordRepository sensitiveWordRepository) {
        this.sensitiveWordRepository = sensitiveWordRepository;
    }

    public FilterResult filter(String text) {
        String source = text == null ? "" : text;
        List<SensitiveWord> words = sensitiveWordRepository.findAll().stream()
                .filter(w -> w.getWord() != null && !w.getWord().trim().isEmpty())
                .sorted(Comparator.comparingInt((SensitiveWord w) -> w.getWord().length()).reversed())
                .collect(Collectors.toList());

        List<String> hitWords = new ArrayList<String>();
        String filtered = source;
        boolean valid = true;
        for (SensitiveWord word : words) {
            String kw = word.getWord().trim();
            if (kw.isEmpty()) continue;
            if (filtered.contains(kw)) {
                hitWords.add(kw);
                if ("review".equalsIgnoreCase(word.getLevel())) {
                    valid = false;
                }
                String mask = repeat('*', Math.max(2, kw.length()));
                filtered = filtered.replace(kw, mask);
            }
        }
        if (!hitWords.isEmpty() && valid) {
            // 命中 block 类型词也视为不通过
            valid = words.stream().anyMatch(w -> hitWords.contains(w.getWord()) && !"review".equalsIgnoreCase(w.getLevel()));
        }
        return new FilterResult(valid, filtered, hitWords);
    }

    private String repeat(char ch, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(ch);
        return sb.toString();
    }

    public static class FilterResult {
        private final boolean valid;
        private final String filteredText;
        private final List<String> hitWords;

        public FilterResult(boolean valid, String filteredText, List<String> hitWords) {
            this.valid = valid;
            this.filteredText = filteredText;
            this.hitWords = hitWords;
        }

        public boolean isValid() { return valid; }
        public String getFilteredText() { return filteredText; }
        public List<String> getHitWords() { return hitWords; }
    }
}
