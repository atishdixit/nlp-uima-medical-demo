package com.example.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts one PDF to text. Pages are rendered one after another on the calling thread (PDFBox documents are
 * not thread-safe) and recognised in parallel on the worker pool. Only a bounded number of rendered pages is
 * held in memory at a time, so a 500-page scan does not need 500 bitmaps of RAM.
 */
public final class PdfConverter {

    /** A failed conversion, with a message fit to show to the user. */
    public static final class ConversionException extends Exception {
        public ConversionException(String message) {
            super(message);
        }

        public ConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Result of a successful conversion. */
    public record Conversion(String text, int pages, int ocrPages, int embeddedTextPages) {
    }

    private record PageText(String text, boolean ocr) {
    }

    /** A page with at least this much embedded text is considered to have a real text layer (AUTO mode). */
    static final int MIN_EMBEDDED_CHARS = 20;
    /** Pixel budget for one rendered page (about 240 MB as 8-bit gray + Java overhead); larger pages are scaled down. */
    static final long MAX_PIXELS_PER_PAGE = 60_000_000L;
    /** Lowest resolution used for oversized pages; below the user-selectable minimum on purpose, so the budget always holds. */
    static final int MIN_EFFECTIVE_DPI = 30;

    private static final Logger log = LoggerFactory.getLogger(PdfConverter.class);

    private final Options options;
    private final ExecutorService pool;
    private final ThreadLocal<OcrEngine> engines;
    private final Semaphore inFlight;

    public PdfConverter(Options options, ExecutorService pool, Supplier<OcrEngine> engineFactory) {
        this.options = options;
        this.pool = pool;
        this.engines = ThreadLocal.withInitial(engineFactory);
        this.inFlight = new Semaphore(Math.max(2, options.threads() * 2));
    }

    public Conversion convert(Path pdf) throws ConversionException {
        try (PDDocument document = load(pdf)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new ConversionException("The PDF has no pages");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            List<CompletableFuture<PageText>> pages = new ArrayList<>(pageCount);
            try {
                for (int i = 0; i < pageCount; i++) {
                    pages.add(startPage(document, renderer, i));
                    failFast(pages); // do not render the rest of a big PDF once a page has already failed
                }
                return collect(pages);
            } finally {
                pages.forEach(f -> f.cancel(true)); // no-op for finished pages; stops queued work after a failure
            }
        } catch (InvalidPasswordException e) {
            throw new ConversionException("The PDF is password-protected", e);
        } catch (IOException e) {
            throw new ConversionException("The file is not a readable PDF (" + e.getMessage() + ")", e);
        }
    }

    private PDDocument load(Path pdf) throws IOException {
        // temp-file backed streams keep big scanned PDFs out of the Java heap
        return Loader.loadPDF(pdf.toFile(), "", null, null, IOUtils.createTempFileOnlyStreamCache());
    }

    private CompletableFuture<PageText> startPage(PDDocument document, PDFRenderer renderer, int index) throws ConversionException {
        int pageNumber = index + 1;
        try {
            if (options.mode() == Options.Mode.AUTO) {
                String embedded = embeddedText(document, pageNumber);
                if (embedded != null) {
                    return CompletableFuture.completedFuture(new PageText(embedded, false));
                }
            }
            int dpi = effectiveDpi(document.getPage(index), options.dpi());
            if (dpi < options.dpi()) {
                log.warn("Page {} is very large; rendering at {} DPI instead of {}", pageNumber, dpi, options.dpi());
            }
            BufferedImage image = renderer.renderImageWithDPI(index, dpi, ImageType.GRAY);
            inFlight.acquire();
            return CompletableFuture.supplyAsync(() -> recognise(image, dpi), pool)
                    .whenComplete((r, e) -> inFlight.release())
                    .thenApply(text -> new PageText(text, true))
                    .exceptionally(e -> {
                        throw new CompletionException(new ConversionException("Page " + pageNumber + ": " + rootMessage(e), e));
                    });
        } catch (IOException | RuntimeException e) {
            throw new ConversionException("Page " + pageNumber + " could not be rendered: " + rootMessage(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversionException("Interrupted", e);
        }
    }

    private String recognise(BufferedImage image, int dpi) {
        try {
            return engines.get().recognize(image, dpi);
        } catch (OcrEngine.OcrException e) {
            throw new CompletionException(e);
        }
    }

    private Conversion collect(List<CompletableFuture<PageText>> futures) throws ConversionException {
        List<String> cleaned = new ArrayList<>(futures.size());
        int ocr = 0;
        int embedded = 0;
        for (CompletableFuture<PageText> future : futures) {
            PageText page = await(future);
            cleaned.add(TextCleaner.cleanPage(page.text()));
            if (page.ocr()) {
                ocr++;
            } else {
                embedded++;
            }
        }
        return new Conversion(TextCleaner.join(cleaned, options.pageMarkers()), futures.size(), ocr, embedded);
    }

    private void failFast(List<CompletableFuture<PageText>> futures) throws ConversionException {
        for (CompletableFuture<PageText> future : futures) {
            if (future.isCompletedExceptionally()) {
                await(future); // throws the ConversionException
            }
        }
    }

    private static PageText await(CompletableFuture<PageText> future) throws ConversionException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : e.getCause();
            if (cause instanceof ConversionException ce) {
                throw ce;
            }
            throw new ConversionException(rootMessage(cause), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversionException("Interrupted", e);
        }
    }

    /** The page's own text if it has a real text layer, otherwise null. */
    private static String embeddedText(PDDocument document, int pageNumber) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        String text = stripper.getText(document);
        return text != null && text.strip().length() >= MIN_EMBEDDED_CHARS ? text : null;
    }

    /** Lowers the DPI for huge pages (posters, maps) so one page cannot exhaust the heap. */
    static int effectiveDpi(PDPage page, int requestedDpi) {
        PDRectangle box = page.getCropBox();
        double widthInches = box.getWidth() / 72.0;
        double heightInches = box.getHeight() / 72.0;
        double pixels = widthInches * requestedDpi * heightInches * requestedDpi;
        if (pixels <= MAX_PIXELS_PER_PAGE || widthInches <= 0 || heightInches <= 0) {
            return requestedDpi;
        }
        double scaled = Math.sqrt(MAX_PIXELS_PER_PAGE / (widthInches * heightInches));
        return Math.max(MIN_EFFECTIVE_DPI, (int) Math.floor(scaled));
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
