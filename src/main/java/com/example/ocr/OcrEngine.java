package com.example.ocr;

import java.awt.image.BufferedImage;

/**
 * Turns one page image into text. An instance is used by one thread at a time (the converter keeps one
 * per worker thread), so implementations need not be thread-safe.
 */
public interface OcrEngine {

    /** Failure of the OCR engine itself (missing native library, bad language data, ...). */
    class OcrException extends Exception {
        public OcrException(String message, Throwable cause) {
            super(message, cause);
        }

        public OcrException(String message) {
            super(message);
        }
    }

    /** @param dpi the resolution the image was rendered at, so the engine can size characters correctly */
    String recognize(BufferedImage page, int dpi) throws OcrException;
}
