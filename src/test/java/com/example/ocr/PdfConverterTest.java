package com.example.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ocr.PdfConverter.Conversion;
import com.example.ocr.PdfConverter.ConversionException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The conversion logic, driven by a fake OCR engine so it is fast and independent of the native library. */
class PdfConverterTest {

    @TempDir
    Path dir;

    private ExecutorService pool;

    @BeforeEach
    void startPool() {
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private Options options(Options.Mode mode, int threads, boolean markers) {
        return new Options(dir, dir.resolve("out"), dir.resolve("tessdata"), "eng", 100, threads, mode, false, false, markers);
    }

    private PdfConverter converter(Options options, OcrEngine engine) {
        return new PdfConverter(options, pool, () -> engine);
    }

    private static OcrEngine failIfUsed() {
        return (image, dpi) -> {
            throw new OcrEngine.OcrException("the OCR engine must not be used here");
        };
    }

    @Test
    void ocrModeRunsEveryPageThroughTheEngineEvenWhenThePdfHasText() throws Exception {
        Path pdf = TestPdfs.digital(dir.resolve("a.pdf"), "first page text here", "second page text here");
        AtomicInteger calls = new AtomicInteger();
        OcrEngine engine = (image, dpi) -> "ocr result " + calls.incrementAndGet();

        Conversion c = converter(options(Options.Mode.OCR, 2, false), engine).convert(pdf);

        assertThat(calls).hasValue(2);
        assertThat(c).extracting(Conversion::pages, Conversion::ocrPages, Conversion::embeddedTextPages).containsExactly(2, 2, 0);
        assertThat(c.text()).contains("ocr result");
        assertThat(c.text()).doesNotContain("first page text");
    }

    @Test
    void autoModeUsesTheTextLayerAndNeverCallsTheEngineForDigitalPdfs() throws Exception {
        Path pdf = TestPdfs.digital(dir.resolve("digital.pdf"), "The quick brown fox jumps", "over the lazy dog again");

        Conversion c = converter(options(Options.Mode.AUTO, 2, false), failIfUsed()).convert(pdf);

        assertThat(c).extracting(Conversion::pages, Conversion::ocrPages, Conversion::embeddedTextPages).containsExactly(2, 0, 2);
        assertThat(c.text()).isEqualTo("The quick brown fox jumps\n\nover the lazy dog again\n");
    }

    @Test
    void autoModeOcrsOnlyThePagesWithoutText() throws Exception {
        Path pdf = TestPdfs.mixed(dir.resolve("mixed.pdf"), "This page has a real text layer", List.of("scanned words"));
        OcrEngine engine = (image, dpi) -> "text from ocr";

        Conversion c = converter(options(Options.Mode.AUTO, 2, false), engine).convert(pdf);

        assertThat(c).extracting(Conversion::pages, Conversion::ocrPages, Conversion::embeddedTextPages).containsExactly(2, 1, 1);
        assertThat(c.text()).isEqualTo("This page has a real text layer\n\ntext from ocr\n");
    }

    @Test
    void autoModeTreatsAnAlmostEmptyTextLayerAsScanned() throws Exception {
        Path pdf = TestPdfs.digital(dir.resolve("tiny.pdf"), "12"); // page number only: fewer than 20 characters
        Conversion c = converter(options(Options.Mode.AUTO, 1, false), (i, d) -> "recognised").convert(pdf);
        assertThat(c.ocrPages()).isEqualTo(1);
    }

    @Test
    void pagesComeBackInDocumentOrderEvenWhenTheyFinishOutOfOrder() throws Exception {
        int pages = 12;
        Path pdf = TestPdfs.digitalWithDistinctPageSizes(dir.resolve("ordered.pdf"), pages);
        // each page has its own width, so the fake engine can tell which page it was given; random delays shuffle completion order
        OcrEngine engine = (BufferedImage image, int dpi) -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 40));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "page-with-width-" + image.getWidth();
        };

        Conversion c = converter(options(Options.Mode.OCR, 4, false), engine).convert(pdf);

        List<String> lines = c.text().lines().filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(pages);
        List<String> sorted = lines.stream().sorted((a, b) -> Integer.compare(width(a), width(b))).toList();
        assertThat(lines).isEqualTo(sorted);
    }

    private static int width(String line) {
        return Integer.parseInt(line.substring(line.lastIndexOf('-') + 1));
    }

    @Test
    void pageMarkersLabelEveryPage() throws Exception {
        Path pdf = TestPdfs.blank(dir.resolve("blank.pdf"), 2);
        Conversion c = converter(options(Options.Mode.OCR, 1, true), (i, d) -> "").convert(pdf);
        assertThat(c.text()).isEqualTo("=== Page 1 ===\n\n=== Page 2 ===\n");
    }

    @Test
    void blankPagesGiveEmptyTextNotAnError() throws Exception {
        Path pdf = TestPdfs.blank(dir.resolve("blank.pdf"), 3);
        Conversion c = converter(options(Options.Mode.OCR, 2, false), (i, d) -> "   \n ").convert(pdf);
        assertThat(c.text()).isEmpty();
        assertThat(c.pages()).isEqualTo(3);
    }

    @Test
    void aPasswordProtectedPdfIsReportedNotCrashed() throws Exception {
        Path pdf = TestPdfs.passwordProtected(dir.resolve("locked.pdf"), "s3cret");
        assertThatThrownBy(() -> converter(options(Options.Mode.OCR, 1, false), failIfUsed()).convert(pdf))
                .isInstanceOf(ConversionException.class).hasMessageContaining("password-protected");
    }

    @Test
    void aFileThatIsNotAPdfIsReported() throws Exception {
        Path bad = TestPdfs.notAPdf(dir.resolve("fake.pdf"));
        assertThatThrownBy(() -> converter(options(Options.Mode.OCR, 1, false), failIfUsed()).convert(bad))
                .isInstanceOf(ConversionException.class).hasMessageContaining("not a readable PDF");
    }

    @Test
    void aTruncatedPdfIsReportedOrRepairedButNeverCrashes() throws Exception {
        Path good = TestPdfs.digital(dir.resolve("good.pdf"), "some text that is long enough to keep");
        Path cut = TestPdfs.truncated(good, dir.resolve("cut.pdf"));
        try {
            Conversion c = converter(options(Options.Mode.AUTO, 1, false), (i, d) -> "x").convert(cut);
            assertThat(c.pages()).isGreaterThanOrEqualTo(0); // PDFBox may salvage it
        } catch (ConversionException expected) {
            assertThat(expected.getMessage()).isNotBlank();
        }
    }

    @Test
    void anEmptyFileIsReported() throws Exception {
        Path empty = dir.resolve("empty.pdf");
        java.nio.file.Files.write(empty, new byte[0]);
        assertThatThrownBy(() -> converter(options(Options.Mode.OCR, 1, false), failIfUsed()).convert(empty))
                .isInstanceOf(ConversionException.class);
    }

    @Test
    void aPdfWithoutPagesIsReported() throws Exception {
        Path pdf = TestPdfs.noPages(dir.resolve("nopages.pdf"));
        assertThatThrownBy(() -> converter(options(Options.Mode.OCR, 1, false), failIfUsed()).convert(pdf))
                .isInstanceOf(ConversionException.class);
    }

    @Test
    void anEngineFailureNamesThePageAndStopsEarly() throws Exception {
        Path pdf = TestPdfs.blank(dir.resolve("many.pdf"), 30);
        AtomicInteger calls = new AtomicInteger();
        OcrEngine engine = (image, dpi) -> {
            int n = calls.incrementAndGet();
            if (n >= 3) {
                throw new OcrEngine.OcrException("engine exploded");
            }
            return "ok";
        };

        assertThatThrownBy(() -> converter(options(Options.Mode.OCR, 1, false), engine).convert(pdf))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("Page ")
                .hasMessageContaining("engine exploded");
        assertThat(calls.get()).as("should not keep recognising all 30 pages after a failure").isLessThan(30);
    }

    @Test
    void oneConverterHandlesManyDocumentsInARow() throws Exception {
        PdfConverter converter = converter(options(Options.Mode.OCR, 3, false), (i, d) -> "words");
        for (int n = 0; n < 5; n++) {
            Path pdf = TestPdfs.blank(dir.resolve("doc" + n + ".pdf"), 4);
            assertThat(converter.convert(pdf).pages()).isEqualTo(4);
        }
    }

    // ---------------------------------------------------------------- huge pages

    @Test
    void hugePagesAreRenderedAtAReducedResolutionInsteadOfRunningOutOfMemory() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage poster = new PDPage(new PDRectangle(200 * 72, 200 * 72)); // 200 x 200 inches
            doc.addPage(poster);
            int dpi = PdfConverter.effectiveDpi(poster, 300);
            double pixels = 200.0 * dpi * 200.0 * dpi;
            assertThat(dpi).isLessThan(300).isGreaterThanOrEqualTo(PdfConverter.MIN_EFFECTIVE_DPI);
            assertThat(pixels).isLessThanOrEqualTo(PdfConverter.MAX_PIXELS_PER_PAGE * 1.05);
        }
    }

    @Test
    void normalPagesKeepTheRequestedResolution() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage a4 = new PDPage(PDRectangle.A4);
            doc.addPage(a4);
            assertThat(PdfConverter.effectiveDpi(a4, 300)).isEqualTo(300);
            assertThat(PdfConverter.effectiveDpi(a4, 600)).isEqualTo(600); // A4 at 600 DPI = 34 MP, under the budget
        }
    }
}
