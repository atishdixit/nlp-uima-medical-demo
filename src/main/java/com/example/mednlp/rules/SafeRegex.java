package com.example.mednlp.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs DB-supplied regexes with a wall-clock budget. Rules come from a database that operators can
 * edit, so a pattern such as {@code (a|aa)+$} must not be able to hang a worker thread.
 */
public final class SafeRegex {

    /** Region of a match: the whole match plus the group chosen as its "value". */
    public record Hit(int start, int end, int valueStart, int valueEnd) {
    }

    public static final class RegexTimeoutException extends RuntimeException {
        public RegexTimeoutException(long timeoutMs) {
            super("regex exceeded " + timeoutMs + " ms");
        }
    }

    private SafeRegex() {
    }

    /**
     * Returns every non-empty match. If the budget is exceeded nothing is returned and
     * {@link RegexTimeoutException} is thrown, so callers never see a half-scanned document.
     */
    public static List<Hit> findAll(Pattern pattern, CharSequence text, int valueGroup, long timeoutMs) {
        Matcher matcher = pattern.matcher(new DeadlineCharSequence(text, timeoutMs));
        List<Hit> hits = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) {
                continue; // zero-length matches carry no information and would create empty annotations
            }
            int vs = matcher.start();
            int ve = matcher.end();
            if (valueGroup > 0 && matcher.start(valueGroup) >= 0) {
                vs = matcher.start(valueGroup);
                ve = matcher.end(valueGroup);
            }
            hits.add(new Hit(matcher.start(), matcher.end(), vs, ve));
        }
        return hits;
    }

    private static final class DeadlineCharSequence implements CharSequence {
        private static final int CHECK_MASK = 0xFFF; // look at the clock every 4096 reads

        private final CharSequence delegate;
        private final long timeoutMs;
        private final long deadlineNanos;
        private int reads;

        DeadlineCharSequence(CharSequence delegate, long timeoutMs) {
            this.delegate = delegate;
            this.timeoutMs = timeoutMs;
            this.deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L;
        }

        @Override
        public char charAt(int index) {
            if ((++reads & CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos) {
                throw new RegexTimeoutException(timeoutMs);
            }
            return delegate.charAt(index);
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return delegate.subSequence(start, end);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
