package com.example.ocr;

import java.awt.image.BufferedImage;

/**
 * Wraps an engine with the noise rescue. In AUTO mode a page is recognised as it is; only when that returns
 * (almost) no text from a page that is not blank is it despeckled and tried again, and the better result wins.
 * Clean scans therefore behave exactly as without this wrapper.
 */
public final class PreprocessingOcrEngine implements OcrEngine {

    /** Fewer letters/digits than this counts as "found nothing". */
    static final int MIN_USEFUL_CHARS = 10;

    private final OcrEngine delegate;
    private final Options.Preprocess mode;

    public PreprocessingOcrEngine(OcrEngine delegate, Options.Preprocess mode) {
        this.delegate = delegate;
        this.mode = mode;
    }

    @Override
    public String recognize(BufferedImage page, int dpi) throws OcrException {
        return switch (mode) {
            case OFF -> delegate.recognize(page, dpi);
            case ALWAYS -> delegate.recognize(ImagePreprocessor.despeckle(page), dpi);
            case AUTO -> auto(page, dpi);
        };
    }

    private String auto(BufferedImage page, int dpi) throws OcrException {
        String first = delegate.recognize(page, dpi);
        if (usefulChars(first) >= MIN_USEFUL_CHARS || ImagePreprocessor.isBlank(page)) {
            return first;
        }
        String second = delegate.recognize(ImagePreprocessor.despeckle(page), dpi);
        return usefulChars(second) > usefulChars(first) ? second : first;
    }

    static int usefulChars(String text) {
        if (text == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                n++;
            }
        }
        return n;
    }
}
