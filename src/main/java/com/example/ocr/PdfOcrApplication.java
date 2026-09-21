package com.example.ocr;

import com.example.ocr.OptionsParser.HelpRequested;
import com.example.ocr.OptionsParser.UsageException;
import com.example.ocr.Summary.FileResult;
import com.example.ocr.Summary.Status;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point: {@code java -jar pdf-ocr-extractor.jar [options]}. Relative folders resolve against the current directory. */
public final class PdfOcrApplication {

    private static final Logger log = LoggerFactory.getLogger(PdfOcrApplication.class);

    private PdfOcrApplication() {
    }

    public static void main(String[] args) {
        quietPdfLibraries();
        System.exit(run(args, Path.of("").toAbsolutePath()));
    }

    /** PDFBox logs through java.util.logging and is noisy about harmless font details; real failures are exceptions. */
    private static void quietPdfLibraries() {
        for (String name : new String[] {"org.apache.pdfbox", "org.apache.fontbox"}) {
            java.util.logging.Logger.getLogger(name).setLevel(java.util.logging.Level.SEVERE);
        }
    }

    /** Runs the whole program and returns the exit code; separate from main so tests can call it. */
    static int run(String[] args, Path baseDir) {
        Options options;
        try {
            options = OptionsParser.parse(args, baseDir);
        } catch (HelpRequested e) {
            System.out.println(OptionsParser.USAGE);
            return 0;
        } catch (UsageException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println(OptionsParser.USAGE);
            return 2;
        }

        log.info("PDF OCR extractor: input={} output={} language={} dpi={} threads={} mode={} preprocess={}", options.inputDir(),
                options.outputDir(), options.language(), options.dpi(), options.threads(), options.mode(), options.preprocess());
        try {
            new TessdataManager().ensureLanguages(options.tessdataDir(), options.language());
        } catch (IOException e) {
            log.error("{}", e.getMessage());
            return 2;
        }

        Summary summary = new BatchRunner(options, () -> new PreprocessingOcrEngine(
                new TesseractOcrEngine(options.tessdataDir(), options.language()), options.preprocess())).run();
        report(summary, options);
        return summary.exitCode();
    }

    private static void report(Summary summary, Options options) {
        if (summary.fatalError() != null) {
            log.error("Cannot continue: {}", summary.fatalError());
            return;
        }
        long converted = summary.count(Status.CONVERTED);
        long skipped = summary.count(Status.SKIPPED);
        long failed = summary.count(Status.FAILED);
        log.info("==================== Summary ====================");
        log.info("Converted: {}   Skipped (up to date): {}   Failed: {}   Pages: {}   Time: {}",
                converted, skipped, failed, summary.totalPages(), BatchRunner.format(summary.elapsed()));
        for (FileResult r : summary.results()) {
            if (r.status() == Status.FAILED) {
                log.info("  FAILED  {} : {}", options.inputDir().relativize(r.pdf()), r.message());
            } else if (r.message() != null) {
                log.info("  WARNING {} : {}", options.inputDir().relativize(r.pdf()), r.message());
            }
        }
        if (converted + skipped > 0) {
            log.info("Text files are in: {}", options.outputDir());
        }
    }
}
