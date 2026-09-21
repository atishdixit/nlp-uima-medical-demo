package com.example.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ocr.Summary.FileResult;
import com.example.ocr.Summary.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The batch behaviour: folders, skipping, failure isolation and exit codes. Fake OCR engine, so no native code. */
class BatchRunnerTest {

    @TempDir
    Path work;

    private Path in;
    private Path out;
    private final AtomicInteger engineCalls = new AtomicInteger();
    private final Supplier<OcrEngine> fakeEngine = () -> (image, dpi) -> {
        engineCalls.incrementAndGet();
        return "ocr text";
    };

    @BeforeEach
    void folders() throws IOException {
        in = Files.createDirectories(work.resolve("input_document"));
        out = work.resolve("output_document");
    }

    private Summary run(Options.Mode mode, boolean force, boolean recursive, Supplier<OcrEngine> engine) {
        Options o = new Options(in, out, work.resolve("tessdata"), "eng", 100, 2, mode, force, recursive, false);
        return new BatchRunner(o, engine).run();
    }

    private Summary run() {
        return run(Options.Mode.AUTO, false, false, fakeEngine);
    }

    private static FileResult result(Summary s, String pdfName) {
        return s.results().stream().filter(r -> r.pdf().getFileName().toString().equals(pdfName)).findFirst().orElseThrow();
    }

    private List<String> outputNames() throws IOException {
        if (!Files.isDirectory(out)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(out)) {
            return s.filter(Files::isRegularFile).map(p -> out.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
    }

    @Test
    void convertsEveryPdfIntoAUtf8TextFileWithTheSameName() throws Exception {
        TestPdfs.digital(in.resolve("report one.pdf"), "First document with some text");
        TestPdfs.digital(in.resolve("second.PDF"), "Second document with some text");

        Summary s = run();

        assertThat(s.exitCode()).isZero();
        assertThat(s.count(Status.CONVERTED)).isEqualTo(2);
        assertThat(outputNames()).containsExactly("report one.txt", "second.txt");
        assertThat(Files.readString(out.resolve("report one.txt"), StandardCharsets.UTF_8)).isEqualTo("First document with some text\n");
        assertThat(engineCalls).as("digital PDFs in auto mode need no OCR; the one call is the start-up self-check").hasValue(1);
    }

    @Test
    void createsTheOutputFolderAndLeavesNoTemporaryFiles() throws Exception {
        TestPdfs.digital(in.resolve("a.pdf"), "some text that is long enough");
        assertThat(out).doesNotExist();
        run();
        assertThat(out).isDirectory();
        assertThat(outputNames()).noneMatch(n -> n.endsWith(".tmp"));
    }

    @Test
    void aMissingInputFolderIsCreatedAndIsNotAnError() throws Exception {
        Files.delete(in);
        Summary s = run();
        assertThat(in).isDirectory();
        assertThat(s.exitCode()).isZero();
        assertThat(s.results()).isEmpty();
    }

    @Test
    void anEmptyInputFolderIsNotAnError() {
        Summary s = run();
        assertThat(s.exitCode()).isZero();
        assertThat(s.results()).isEmpty();
        assertThat(engineCalls).hasValue(0);
    }

    @Test
    void nonPdfFilesAreIgnored() throws Exception {
        Files.writeString(in.resolve("notes.txt"), "hello");
        Files.writeString(in.resolve("photo.jpg"), "not really");
        assertThat(run().results()).isEmpty();
    }

    @Test
    void oneBrokenFileNeverStopsTheOthers() throws Exception {
        TestPdfs.digital(in.resolve("a_good.pdf"), "This one converts without any trouble");
        TestPdfs.notAPdf(in.resolve("b_broken.pdf"));
        TestPdfs.passwordProtected(in.resolve("c_locked.pdf"), "pw");
        TestPdfs.digital(in.resolve("d_good.pdf"), "And so does this one, after the failures");

        Summary s = run();

        assertThat(s.exitCode()).isEqualTo(1);
        assertThat(s.count(Status.CONVERTED)).isEqualTo(2);
        assertThat(s.count(Status.FAILED)).isEqualTo(2);
        assertThat(result(s, "b_broken.pdf").message()).contains("not a readable PDF");
        assertThat(result(s, "c_locked.pdf").message()).contains("password-protected");
        assertThat(outputNames()).containsExactly("a_good.txt", "d_good.txt");
    }

    @Test
    void aFailedFileLeavesNoOutputAndNoTemporaryFile() throws Exception {
        TestPdfs.notAPdf(in.resolve("bad.pdf"));
        run();
        assertThat(outputNames()).isEmpty();
    }

    @Test
    void upToDateOutputsAreSkippedAndForceRedoesThem() throws Exception {
        Path pdf = TestPdfs.digital(in.resolve("a.pdf"), "Text of the first run is long enough");
        run();
        Files.writeString(out.resolve("a.txt"), "EDITED BY HAND");

        Summary second = run();
        assertThat(second.count(Status.SKIPPED)).isEqualTo(1);
        assertThat(Files.readString(out.resolve("a.txt"))).isEqualTo("EDITED BY HAND");

        Summary forced = run(Options.Mode.AUTO, true, false, fakeEngine);
        assertThat(forced.count(Status.CONVERTED)).isEqualTo(1);
        assertThat(Files.readString(out.resolve("a.txt"))).startsWith("Text of the first run");
        assertThat(pdf).exists();
    }

    @Test
    void aPdfThatChangedAfterItsOutputIsConvertedAgain() throws Exception {
        Path pdf = TestPdfs.digital(in.resolve("a.pdf"), "Original text that is long enough");
        run();
        TestPdfs.digital(pdf, "Replacement text that is long enough");
        Files.setLastModifiedTime(pdf, FileTime.from(Instant.now().plusSeconds(60)));

        Summary s = run();

        assertThat(s.count(Status.CONVERTED)).isEqualTo(1);
        assertThat(Files.readString(out.resolve("a.txt"))).startsWith("Replacement text");
    }

    @Test
    void aFailedRunIsRetriedNextTime() throws Exception {
        Path pdf = TestPdfs.notAPdf(in.resolve("late.pdf"));
        assertThat(run().count(Status.FAILED)).isEqualTo(1);
        TestPdfs.digital(pdf, "Now it is a proper PDF with text");
        Files.setLastModifiedTime(pdf, FileTime.from(Instant.now().plusSeconds(60)));

        assertThat(run().count(Status.CONVERTED)).isEqualTo(1);
    }

    @Test
    void subFoldersAreMirroredOnlyInRecursiveMode() throws Exception {
        TestPdfs.digital(in.resolve("top.pdf"), "Top level document text here");
        Files.createDirectories(in.resolve("2024/january"));
        TestPdfs.digital(in.resolve("2024/january/inner.pdf"), "Nested document text goes here");

        assertThat(run().count(Status.CONVERTED)).isEqualTo(1);
        assertThat(outputNames()).containsExactly("top.txt");

        Summary recursive = run(Options.Mode.AUTO, false, true, fakeEngine);
        assertThat(recursive.count(Status.CONVERTED)).isEqualTo(1);
        assertThat(outputNames()).containsExactly("2024/january/inner.txt", "top.txt");
    }

    @Test
    void aBlankDocumentIsConvertedWithAWarning() throws Exception {
        TestPdfs.blank(in.resolve("blank.pdf"), 2);
        Summary s = run(Options.Mode.OCR, false, false, () -> (i, d) -> "");
        FileResult r = result(s, "blank.pdf");
        assertThat(r.status()).isEqualTo(Status.CONVERTED);
        assertThat(r.message()).contains("No text was recognised");
        assertThat(s.exitCode()).isZero();
        assertThat(Files.readString(out.resolve("blank.txt"))).isEmpty();
    }

    @Test
    void unicodeTextSurvivesTheRoundTrip() throws Exception {
        TestPdfs.blank(in.resolve("u.pdf"), 1);
        run(Options.Mode.OCR, false, false, () -> (i, d) -> "Café naïve – Größe ✓ 温度");
        assertThat(Files.readString(out.resolve("u.txt"), StandardCharsets.UTF_8)).isEqualTo("Café naïve – Größe ✓ 温度\n");
    }

    @Test
    void ifTheOcrEngineCannotStartTheRunStopsWithOneClearErrorAndWritesNothing() throws Exception {
        TestPdfs.blank(in.resolve("a.pdf"), 3);
        TestPdfs.blank(in.resolve("b.pdf"), 3);
        Supplier<OcrEngine> broken = () -> (i, d) -> {
            throw new OcrEngine.OcrException("The Tesseract native library could not be loaded");
        };

        Summary s = run(Options.Mode.OCR, false, false, broken);

        assertThat(s.exitCode()).isEqualTo(2);
        assertThat(s.fatalError()).contains("native library");
        assertThat(outputNames()).isEmpty();
    }

    @Test
    void anEngineThatCannotEvenBeConstructedIsAlsoReportedCleanly() throws Exception {
        TestPdfs.blank(in.resolve("a.pdf"), 1);
        Summary s = run(Options.Mode.OCR, false, false, () -> {
            throw new UnsatisfiedLinkError("no jnidispatch in java.library.path");
        });
        assertThat(s.exitCode()).isEqualTo(2);
        assertThat(s.fatalError()).contains("could not be started");
    }

    @Test
    void theEngineIsNotStartedWhenThereIsNothingToConvert() throws Exception {
        TestPdfs.digital(in.resolve("a.pdf"), "Some text that is long enough here");
        run();
        Summary second = run(Options.Mode.AUTO, false, false, () -> {
            throw new AssertionError("engine must not be created for a run that skips everything");
        });
        assertThat(second.exitCode()).isZero();
        assertThat(second.count(Status.SKIPPED)).isEqualTo(1);
    }

    @Test
    void resultsAreListedInFileNameOrder() throws Exception {
        TestPdfs.digital(in.resolve("c.pdf"), "Third document with enough text");
        TestPdfs.digital(in.resolve("a.pdf"), "First document with enough text");
        TestPdfs.digital(in.resolve("b.pdf"), "Second document with enough text");
        assertThat(run().results()).extracting(r -> r.pdf().getFileName().toString()).containsExactly("a.pdf", "b.pdf", "c.pdf");
    }

    @Test
    void summaryCountsPagesOfConvertedFilesOnly() throws Exception {
        TestPdfs.digital(in.resolve("a.pdf"), "Page one text is here", "Page two text is here", "Page three text is here");
        TestPdfs.notAPdf(in.resolve("b.pdf"));
        Summary s = run();
        assertThat(s.totalPages()).isEqualTo(3);
    }
}
