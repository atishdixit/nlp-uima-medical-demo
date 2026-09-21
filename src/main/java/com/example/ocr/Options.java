package com.example.ocr;

import java.nio.file.Path;

/** Everything the run needs, resolved to absolute paths. Built by {@link OptionsParser}. */
public record Options(
        Path inputDir,
        Path outputDir,
        Path tessdataDir,
        String language,
        int dpi,
        int threads,
        Mode mode,
        boolean force,
        boolean recursive,
        boolean pageMarkers,
        Preprocess preprocess) {

    /** How a page is turned into text. */
    public enum Mode {
        /** Always OCR every page (the default: right for scanned documents). */
        OCR,
        /** Use the PDF's own text layer when a page has one, OCR only the pages that do not. */
        AUTO
    }

    /** Noise clean-up applied to page images before OCR. */
    public enum Preprocess {
        /** Never touch the image. */
        OFF,
        /** OCR as is; if a page that is not blank yields (almost) no text, despeckle it and try again (the default). */
        AUTO,
        /** Despeckle every page first: for batches known to be noisy. Slower. */
        ALWAYS
    }

    public static final int MIN_DPI = 72;
    public static final int MAX_DPI = 600;
    public static final int MAX_THREADS = 32;

    /** Same as the full constructor with the default noise handling ({@link Preprocess#AUTO}). */
    public Options(Path inputDir, Path outputDir, Path tessdataDir, String language, int dpi, int threads, Mode mode,
                   boolean force, boolean recursive, boolean pageMarkers) {
        this(inputDir, outputDir, tessdataDir, language, dpi, threads, mode, force, recursive, pageMarkers, Preprocess.AUTO);
    }
}
