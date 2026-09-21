package com.example.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ocr.OptionsParser.HelpRequested;
import com.example.ocr.OptionsParser.UsageException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OptionsParserTest {

    private static final Path BASE = Path.of("C:/work/app").toAbsolutePath().normalize();

    private static Options parse(String... args) throws Exception {
        return OptionsParser.parse(args, BASE);
    }

    @Test
    void defaultsMatchTheDocumentedFolders() throws Exception {
        Options o = parse();
        assertThat(o.inputDir()).isEqualTo(BASE.resolve("input_document"));
        assertThat(o.outputDir()).isEqualTo(BASE.resolve("output_document"));
        assertThat(o.tessdataDir()).isEqualTo(BASE.resolve("tessdata"));
        assertThat(o.language()).isEqualTo("eng");
        assertThat(o.dpi()).isEqualTo(300);
        assertThat(o.threads()).isBetween(1, 4);
        assertThat(o.mode()).isEqualTo(Options.Mode.OCR);
        assertThat(o.force()).isFalse();
        assertThat(o.recursive()).isFalse();
        assertThat(o.pageMarkers()).isFalse();
    }

    @Test
    void acceptsSeparateAndInlineValues() throws Exception {
        Options o = parse("--input", "in", "--output=out", "--lang", "eng+deu", "--dpi=200", "--threads", "3", "--mode=auto");
        assertThat(o.inputDir()).isEqualTo(BASE.resolve("in"));
        assertThat(o.outputDir()).isEqualTo(BASE.resolve("out"));
        assertThat(o.language()).isEqualTo("eng+deu");
        assertThat(o.dpi()).isEqualTo(200);
        assertThat(o.threads()).isEqualTo(3);
        assertThat(o.mode()).isEqualTo(Options.Mode.AUTO);
    }

    @Test
    void preprocessDefaultsToAutoAndAcceptsTheThreeModes() throws Exception {
        assertThat(parse().preprocess()).isEqualTo(Options.Preprocess.AUTO);
        assertThat(parse("--preprocess", "off").preprocess()).isEqualTo(Options.Preprocess.OFF);
        assertThat(parse("--preprocess=ALWAYS").preprocess()).isEqualTo(Options.Preprocess.ALWAYS);
        assertThatThrownBy(() -> parse("--preprocess", "sometimes")).isInstanceOf(UsageException.class)
                .hasMessageContaining("'auto', 'off' or 'always'");
    }

    @Test
    void switchesWork() throws Exception {
        Options o = parse("--force", "--recursive", "--page-markers");
        assertThat(o.force()).isTrue();
        assertThat(o.recursive()).isTrue();
        assertThat(o.pageMarkers()).isTrue();
        assertThat(parse("--force=false").force()).isFalse();
        assertThat(parse("--recursive=yes").recursive()).isTrue();
    }

    @Test
    void absolutePathsAreKeptAndRelativeOnesResolveAgainstTheBaseFolder() throws Exception {
        Path abs = Path.of("D:/scans").toAbsolutePath().normalize();
        assertThat(parse("--input", abs.toString()).inputDir()).isEqualTo(abs);
        assertThat(parse("--output", "../elsewhere").outputDir()).isEqualTo(BASE.getParent().resolve("elsewhere"));
    }

    @Test
    void modeIsCaseInsensitive() throws Exception {
        assertThat(parse("--mode", "AUTO").mode()).isEqualTo(Options.Mode.AUTO);
        assertThat(parse("--mode", "Ocr").mode()).isEqualTo(Options.Mode.OCR);
    }

    @Test
    void helpIsRequestedByAnyOfItsSpellings() {
        for (String flag : new String[] {"--help", "-h", "/?"}) {
            assertThatThrownBy(() -> parse(flag)).isInstanceOf(HelpRequested.class);
        }
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> parse("--bogus")).isInstanceOf(UsageException.class).hasMessageContaining("Unknown option: --bogus");
        assertThatThrownBy(() -> parse("input_document")).isInstanceOf(UsageException.class).hasMessageContaining("Unknown option");
        assertThatThrownBy(() -> parse("--input")).isInstanceOf(UsageException.class).hasMessageContaining("needs a value");
        assertThatThrownBy(() -> parse("--input", " ")).isInstanceOf(UsageException.class).hasMessageContaining("non-empty");
        assertThatThrownBy(() -> parse("--dpi", "abc")).isInstanceOf(UsageException.class).hasMessageContaining("whole number");
        assertThatThrownBy(() -> parse("--dpi", "10")).isInstanceOf(UsageException.class).hasMessageContaining("between 72 and 600");
        assertThatThrownBy(() -> parse("--dpi", "601")).isInstanceOf(UsageException.class).hasMessageContaining("between 72 and 600");
        assertThatThrownBy(() -> parse("--threads", "0")).isInstanceOf(UsageException.class).hasMessageContaining("between 1 and 32");
        assertThatThrownBy(() -> parse("--threads", "33")).isInstanceOf(UsageException.class);
        assertThatThrownBy(() -> parse("--mode", "fast")).isInstanceOf(UsageException.class).hasMessageContaining("'ocr' or 'auto'");
        assertThatThrownBy(() -> parse("--force=maybe")).isInstanceOf(UsageException.class).hasMessageContaining("switch");
    }

    @Test
    void rejectsLanguageCodesThatCouldEscapeTheTessdataFolder() {
        for (String bad : new String[] {"../evil", "eng;rm", "e", "eng+", "+eng", "eng/deu", "eng deu"}) {
            assertThatThrownBy(() -> parse("--lang", bad)).as(bad).isInstanceOf(UsageException.class);
        }
    }

    @Test
    void inputAndOutputMustDiffer() {
        assertThatThrownBy(() -> parse("--input", "docs", "--output", "docs"))
                .isInstanceOf(UsageException.class).hasMessageContaining("must be different");
    }
}
