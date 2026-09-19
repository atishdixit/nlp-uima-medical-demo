package com.example.mednlp.rules;

/**
 * Whitespace-normalised view of a text: every run of whitespace (including tabs, newlines and
 * non-breaking spaces) becomes one space. Offset maps let matches found on the normalised text be
 * reported against the original text, so "chest   pain" and "chest\npain" still match "chest pain"
 * while annotation offsets stay valid for the caller.
 */
public final class NormalizedText {

    private final String text;
    private final int[] toOriginal;
    private final int[] fromOriginal;

    private NormalizedText(String text, int[] toOriginal, int[] fromOriginal) {
        this.text = text;
        this.toOriginal = toOriginal;
        this.fromOriginal = fromOriginal;
    }

    public static NormalizedText of(CharSequence source) {
        int n = source.length();
        StringBuilder sb = new StringBuilder(n);
        int[] toOriginal = new int[n];
        int[] fromOriginal = new int[n];
        boolean previousWasSpace = false;
        for (int i = 0; i < n; i++) {
            char c = source.charAt(i);
            if (isSpace(c)) {
                if (!previousWasSpace) {
                    toOriginal[sb.length()] = i;
                    sb.append(' ');
                    previousWasSpace = true;
                }
                fromOriginal[i] = sb.length() - 1;
            } else {
                toOriginal[sb.length()] = i;
                fromOriginal[i] = sb.length();
                sb.append(c);
                previousWasSpace = false;
            }
        }
        return new NormalizedText(sb.toString(), toOriginal, fromOriginal);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == ' ' || c == ' ' || c == ' ';
    }

    public String text() {
        return text;
    }

    /** Original offset of the normalised character at {@code index}. */
    public int toOriginalStart(int index) {
        return toOriginal[index];
    }

    /** Original exclusive end offset for a normalised exclusive end. */
    public int toOriginalEnd(int endExclusive) {
        return toOriginal[endExclusive - 1] + 1;
    }

    public int fromOriginalStart(int originalIndex) {
        return fromOriginal[originalIndex];
    }

    public int fromOriginalEnd(int originalEndExclusive) {
        return fromOriginal[originalEndExclusive - 1] + 1;
    }
}
