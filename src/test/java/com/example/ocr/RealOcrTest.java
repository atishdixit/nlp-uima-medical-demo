package com.example.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ocr.PdfConverter.Conversion;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end with the REAL Tesseract engine (native library and eng.traineddata from ./tessdata). The pages are
 * generated bitmaps with no text layer, so the only way to get the words back is OCR.
 */
class RealOcrTest {

    static final Path TESSDATA = Path.of("tessdata").toAbsolutePath();

    @TempDir
    Path dir;

    private ExecutorService pool;

    @BeforeAll
    static void modelIsAvailable() throws Exception {
        new TessdataManager().ensureLanguages(TESSDATA, "eng"); // present in the repo; downloads only if somebody deleted it
    }

    @BeforeEach
    void startPool() {
        pool = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private Options options(Options.Mode mode, int threads, boolean markers) {
        return new Options(dir, dir.resolve("out"), TESSDATA, "eng", 300, threads, mode, false, false, markers);
    }

    private PdfConverter converter(Options options) {
        return new PdfConverter(options, pool, () -> new TesseractOcrEngine(TESSDATA, "eng"));
    }

    /** Lower-case with single spaces, so assertions do not depend on line breaks or capitalisation. */
    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsAScannedPageWithNoTextLayer() throws Exception {
        Path pdf = TestPdfs.scanned(dir.resolve("scan.pdf"), List.of(
                "Patient denies fever and cough",
                "Blood pressure 120/80 mmHg",
                "Metformin 500 mg twice daily"));

        Conversion c = converter(options(Options.Mode.OCR, 1, false)).convert(pdf);

        String text = norm(c.text());
        assertThat(text).contains("patient denies fever and cough");
        assertThat(text).contains("blood pressure 120/80 mmhg");
        assertThat(text).contains("metformin 500 mg twice daily");
        assertThat(c.ocrPages()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsPagesInOrderWhenRecognisedInParallel() throws Exception {
        Path pdf = TestPdfs.scanned(dir.resolve("multi.pdf"),
                List.of("ALPHA first page"), List.of("BRAVO second page"), List.of("CHARLIE third page"),
                List.of("DELTA fourth page"), List.of("ECHO fifth page"), List.of("FOXTROT sixth page"));

        Conversion c = converter(options(Options.Mode.OCR, 3, true)).convert(pdf);

        assertThat(c.pages()).isEqualTo(6);
        String text = norm(c.text());
        int last = -1;
        for (String word : List.of("alpha", "bravo", "charlie", "delta", "echo", "foxtrot")) {
            int at = text.indexOf(word);
            assertThat(at).as(word + " must be present in: " + text).isGreaterThan(last);
            last = at;
        }
        assertThat(text).contains("=== page 1 ===").contains("=== page 6 ===");
    }

    @Test
    @SuppressWarnings("unchecked")
    void autoModeMixesTheTextLayerAndOcr() throws Exception {
        Path pdf = TestPdfs.mixed(dir.resolve("mixed.pdf"), "Digital page with a real text layer", List.of("Scanned page words here"));

        Conversion c = converter(options(Options.Mode.AUTO, 2, false)).convert(pdf);

        assertThat(c.embeddedTextPages()).isEqualTo(1);
        assertThat(c.ocrPages()).isEqualTo(1);
        String text = norm(c.text());
        assertThat(text).contains("digital page with a real text layer").contains("scanned page words here");
    }

    private PdfConverter converter(Options options, Options.Preprocess preprocess) {
        return new PdfConverter(options, pool, () -> new PreprocessingOcrEngine(new TesseractOcrEngine(TESSDATA, "eng"), preprocess));
    }

    @Test
    void heavySpeckleNoiseIsRescuedByTheAutomaticRetry() throws Exception {
        Path pdf = TestPdfs.scannedWithSpeckle(dir.resolve("dusty.pdf"), List.of(
                "Patient denies fever, headache or nausea.",
                "Vitals: BP 148/92 mmHg, HR 88, Temp 98.6",
                "Metformin 500 mg twice daily"), 90_000);

        Conversion rescued = converter(options(Options.Mode.OCR, 1, false), Options.Preprocess.AUTO).convert(pdf);
        String text = norm(rescued.text());
        assertThat(text).contains("patient denies fever").contains("metformin 500 mg twice daily").contains("148/92");

        Conversion always = converter(options(Options.Mode.OCR, 1, false), Options.Preprocess.ALWAYS).convert(pdf);
        assertThat(norm(always.text())).contains("metformin 500 mg twice daily");
    }

    @Test
    void aCleanPageGivesTheSameTextWithAndWithoutPreprocessing() throws Exception {
        @SuppressWarnings("unchecked")
        Path pdf = TestPdfs.scanned(dir.resolve("clean.pdf"), List.of("Patient denies fever and cough", "Metformin 500 mg twice daily"));
        String off = norm(converter(options(Options.Mode.OCR, 1, false), Options.Preprocess.OFF).convert(pdf).text());
        String auto = norm(converter(options(Options.Mode.OCR, 1, false), Options.Preprocess.AUTO).convert(pdf).text());
        assertThat(auto).isEqualTo(off).contains("metformin 500 mg twice daily");
    }

    @Test
    void aBlankPageIsHandledWithoutErrors() throws Exception {
        Path pdf = TestPdfs.blank(dir.resolve("blank.pdf"), 2);
        Conversion c = converter(options(Options.Mode.OCR, 2, false)).convert(pdf);
        assertThat(c.pages()).isEqualTo(2);
        assertThat(c.text().strip().length()).as("a blank page should yield (almost) no text").isLessThan(10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theWholeProgramWorksFromTheCommandLine() throws Exception {
        Path in = Files.createDirectories(dir.resolve("input_document"));
        TestPdfs.scanned(in.resolve("visit.pdf"), List.of("Discharge summary", "Follow up in two weeks"));
        TestPdfs.notAPdf(in.resolve("broken.pdf"));

        int code = PdfOcrApplication.run(new String[] {"--tessdata", TESSDATA.toString(), "--threads", "2"}, dir);

        assertThat(code).as("one file failed, so exit code 1").isEqualTo(1);
        Path txt = dir.resolve("output_document/visit.txt");
        assertThat(txt).exists();
        String text = norm(Files.readString(txt, StandardCharsets.UTF_8));
        assertThat(text).contains("discharge summary").contains("follow up in two weeks");
        assertThat(dir.resolve("output_document/broken.txt")).doesNotExist();

        // second run: the good file is up to date, only the broken one is looked at again
        assertThat(PdfOcrApplication.run(new String[] {"--tessdata", TESSDATA.toString()}, dir)).isEqualTo(1);
    }

    @Test
    void theProgramReturnsZeroWhenEverythingWorks() throws Exception {
        Path in = Files.createDirectories(dir.resolve("input_document"));
        TestPdfs.digital(in.resolve("digital.pdf"), "A digital document with enough text");
        assertThat(PdfOcrApplication.run(new String[] {"--mode", "auto", "--tessdata", TESSDATA.toString()}, dir)).isZero();
        assertThat(Files.readString(dir.resolve("output_document/digital.txt"))).isEqualTo("A digital document with enough text\n");
    }

    @Test
    void usageErrorsAndHelpHaveTheirOwnExitCodes() {
        assertThat(PdfOcrApplication.run(new String[] {"--help"}, dir)).isZero();
        assertThat(PdfOcrApplication.run(new String[] {"--dpi", "5"}, dir)).isEqualTo(2);
        assertThat(PdfOcrApplication.run(new String[] {"--nonsense"}, dir)).isEqualTo(2);
    }
}
