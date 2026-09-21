package com.example.ocr;

import com.example.ocr.PdfConverter.Conversion;
import com.example.ocr.PdfConverter.ConversionException;
import com.example.ocr.Summary.FileResult;
import com.example.ocr.Summary.Status;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts every PDF in the input folder. One bad file never stops the others: it is reported and the run
 * continues. Output is written to a temporary file and moved into place, so a crash can never leave a
 * half-written .txt that a later run would mistake for a finished one.
 */
public final class BatchRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

    private final Options options;
    private final Supplier<OcrEngine> engineFactory;

    public BatchRunner(Options options, Supplier<OcrEngine> engineFactory) {
        this.options = options;
        this.engineFactory = engineFactory;
    }

    public Summary run() {
        long started = System.nanoTime();
        try {
            Files.createDirectories(options.outputDir());
            if (!Files.isDirectory(options.inputDir())) {
                Files.createDirectories(options.inputDir());
                log.info("The input folder did not exist and was created: {}", options.inputDir());
                log.info("Put your PDF files there and run again.");
                return new Summary(List.of(), since(started), null);
            }
            List<Path> pdfs = PdfScanner.findPdfs(options.inputDir(), options.recursive());
            if (pdfs.isEmpty()) {
                log.info("No PDF files found in {}{}", options.inputDir(), options.recursive() ? " (including sub-folders)" : "");
                return new Summary(List.of(), since(started), null);
            }
            log.info("Found {} PDF file(s) in {}", pdfs.size(), options.inputDir());

            List<Path> todo = new ArrayList<>();
            List<FileResult> results = new ArrayList<>();
            for (Path pdf : pdfs) {
                Path output = PdfScanner.outputFor(options.inputDir(), options.outputDir(), pdf);
                if (isUpToDate(pdf, output)) {
                    log.info("Skipping {} (an up-to-date text file exists; use --force to redo)", options.inputDir().relativize(pdf));
                    results.add(new FileResult(pdf, output, Status.SKIPPED, 0, 0, 0, 0, null, Duration.ZERO));
                } else {
                    todo.add(pdf);
                }
            }

            if (!todo.isEmpty()) {
                String problem = preflight();
                if (problem != null) {
                    return Summary.fatal(problem, since(started));
                }
                results.addAll(convertAll(todo));
            }
            results.sort((a, b) -> a.pdf().compareTo(b.pdf()));
            return new Summary(results, since(started), null);
        } catch (IOException e) {
            return Summary.fatal("File system problem: " + e.getMessage(), since(started));
        }
    }

    private List<FileResult> convertAll(List<Path> todo) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threads = runnable -> {
            Thread t = new Thread(runnable, "ocr-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        ExecutorService pool = Executors.newFixedThreadPool(options.threads(), threads);
        try {
            PdfConverter converter = new PdfConverter(options, pool, engineFactory);
            List<FileResult> results = new ArrayList<>();
            int index = 0;
            for (Path pdf : todo) {
                results.add(convertOne(converter, pdf, ++index, todo.size()));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private FileResult convertOne(PdfConverter converter, Path pdf, int index, int total) {
        Path output = PdfScanner.outputFor(options.inputDir(), options.outputDir(), pdf);
        String label = options.inputDir().relativize(pdf).toString();
        long started = System.nanoTime();
        log.info("[{}/{}] Converting {} ...", index, total, label);
        try {
            Conversion conversion = converter.convert(pdf);
            write(output, conversion.text());
            String warning = conversion.text().isBlank() ? "No text was recognised (blank or unreadable pages)" : null;
            Duration elapsed = since(started);
            log.info("[{}/{}] {} -> {} ({} pages, {} characters, {}){}", index, total, label, options.outputDir().relativize(output),
                    conversion.pages(), conversion.text().length(), format(elapsed), warning == null ? "" : "  WARNING: " + warning);
            return new FileResult(pdf, output, Status.CONVERTED, conversion.pages(), conversion.ocrPages(),
                    conversion.embeddedTextPages(), conversion.text().length(), warning, elapsed);
        } catch (ConversionException e) {
            return failed(pdf, output, label, e.getMessage(), started, index, total);
        } catch (IOException e) {
            return failed(pdf, output, label, "Could not write the text file: " + e.getMessage(), started, index, total);
        } catch (OutOfMemoryError e) {
            return failed(pdf, output, label, "Out of memory. Try a lower --dpi or --threads, or start Java with more memory (JAVA_OPTS=-Xmx4g).",
                    started, index, total);
        } catch (RuntimeException e) {
            log.error("Unexpected error while converting {}", label, e);
            return failed(pdf, output, label, "Unexpected error: " + e, started, index, total);
        }
    }

    private FileResult failed(Path pdf, Path output, String label, String message, long started, int index, int total) {
        log.error("[{}/{}] FAILED {}: {}", index, total, label, message);
        return new FileResult(pdf, output, Status.FAILED, 0, 0, 0, 0, message, since(started));
    }

    /** An output is current when it exists, is not older than its PDF, and --force was not given. */
    private boolean isUpToDate(Path pdf, Path output) throws IOException {
        if (options.force() || !Files.isRegularFile(output)) {
            return false;
        }
        return Files.getLastModifiedTime(output).compareTo(Files.getLastModifiedTime(pdf)) >= 0;
    }

    /** Runs the OCR engine once on a blank image so a missing native library is one clear error, not one per page. */
    private String preflight() {
        try {
            engineFactory.get().recognize(new BufferedImage(64, 64, BufferedImage.TYPE_BYTE_GRAY), options.dpi());
            return null;
        } catch (OcrEngine.OcrException e) {
            return e.getMessage();
        } catch (RuntimeException | LinkageError e) {
            return "The OCR engine could not be started: " + e;
        }
    }

    private static void write(Path output, String text) throws IOException {
        Files.createDirectories(output.getParent());
        Path temp = output.resolveSibling(output.getFileName() + ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static Duration since(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    static String format(Duration d) {
        long ms = d.toMillis();
        return ms < 1000 ? ms + " ms" : String.format("%.1f s", ms / 1000.0);
    }
}
