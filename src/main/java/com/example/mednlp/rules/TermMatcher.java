package com.example.mednlp.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;

/**
 * Multi-term dictionary matcher (Aho-Corasick) that resolves overlaps by longest match and
 * only accepts matches on word boundaries. Terms may be case-insensitive or case-sensitive
 * (abbreviations such as "MI" or "SOB"); each mode has its own trie. Immutable and thread-safe.
 *
 * <p>Case folding is done here, character by character with {@link Character#toLowerCase(char)},
 * so text and keywords fold identically and offsets never shift (locale-sensitive
 * {@code String.toLowerCase} can change the length).
 */
public final class TermMatcher<T> {

    /** A resolved match; {@code end} is exclusive. */
    public record Match<T>(int start, int end, String text, T payload) {
    }

    private record Candidate<T>(int start, int end, T payload, boolean caseSensitive) {
        int length() {
            return end - start;
        }
    }

    private final Trie insensitiveTrie;
    private final Trie sensitiveTrie;
    private final Map<String, T> insensitive;
    private final Map<String, T> sensitive;

    private TermMatcher(Map<String, T> insensitive, Map<String, T> sensitive) {
        this.insensitive = insensitive;
        this.sensitive = sensitive;
        this.insensitiveTrie = insensitive.isEmpty() ? null : Trie.builder().addKeywords(insensitive.keySet()).build();
        this.sensitiveTrie = sensitive.isEmpty() ? null : Trie.builder().addKeywords(sensitive.keySet()).build();
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public int termCount() {
        return insensitive.size() + sensitive.size();
    }

    /** Finds all non-overlapping, word-bounded matches in {@code text}, ordered by start offset. */
    public List<Match<T>> findAll(String text) {
        if (text.isEmpty() || termCount() == 0) {
            return List.of();
        }
        List<Candidate<T>> candidates = new ArrayList<>();
        if (insensitiveTrie != null) {
            String lowered = lower(text);
            for (Emit emit : insensitiveTrie.parseText(lowered)) {
                addIfWordBounded(candidates, text, emit, insensitive.get(emit.getKeyword()), false);
            }
        }
        if (sensitiveTrie != null) {
            for (Emit emit : sensitiveTrie.parseText(text)) {
                addIfWordBounded(candidates, text, emit, sensitive.get(emit.getKeyword()), true);
            }
        }
        return resolveOverlaps(text, candidates);
    }

    private void addIfWordBounded(List<Candidate<T>> out, String text, Emit emit, T payload, boolean cs) {
        int start = emit.getStart();
        int end = emit.getEnd() + 1;
        boolean leftOk = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean rightOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
        if (leftOk && rightOk && payload != null) {
            out.add(new Candidate<>(start, end, payload, cs));
        }
    }

    private List<Match<T>> resolveOverlaps(String text, List<Candidate<T>> candidates) {
        candidates.sort(Comparator.<Candidate<T>>comparingInt(Candidate::length).reversed()
                .thenComparingInt(Candidate::start)
                .thenComparing(c -> !c.caseSensitive()));
        TreeMap<Integer, Integer> picked = new TreeMap<>(); // start -> end of accepted matches
        List<Match<T>> result = new ArrayList<>();
        for (Candidate<T> c : candidates) {
            if (overlaps(picked, c.start(), c.end())) {
                continue;
            }
            picked.put(c.start(), c.end());
            result.add(new Match<>(c.start(), c.end(), text.substring(c.start(), c.end()), c.payload()));
        }
        result.sort(Comparator.comparingInt(Match::start));
        return result;
    }

    private static boolean overlaps(TreeMap<Integer, Integer> picked, int start, int end) {
        Map.Entry<Integer, Integer> before = picked.floorEntry(start);
        if (before != null && before.getValue() > start) {
            return true;
        }
        Map.Entry<Integer, Integer> after = picked.ceilingEntry(start);
        return after != null && after.getKey() < end;
    }

    static String lower(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    static String collapseWhitespace(String term) {
        return NormalizedText.of(term.strip()).text();
    }

    public static final class Builder<T> {
        private final Map<String, T> insensitive = new HashMap<>();
        private final Map<String, T> sensitive = new HashMap<>();

        /** Adds a term; blank terms are ignored and the first payload registered for a term wins. */
        public Builder<T> add(String term, boolean caseSensitive, T payload) {
            if (term == null || term.isBlank()) {
                return this;
            }
            String key = collapseWhitespace(term);
            if (caseSensitive) {
                sensitive.putIfAbsent(key, payload);
            } else {
                insensitive.putIfAbsent(lower(key), payload);
            }
            return this;
        }

        public TermMatcher<T> build() {
            return new TermMatcher<>(Map.copyOf(insensitive), Map.copyOf(sensitive));
        }
    }
}
