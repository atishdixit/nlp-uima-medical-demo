package com.example.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The despeckle filter and the "retry only when a page comes back empty" logic. */
class PreprocessingTest {

    // ---------------------------------------------------------------- helpers

    private static BufferedImage white(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static int gray(BufferedImage img, int x, int y) {
        return img.getRaster().getSample(x, y, 0);
    }

    private static long darkPixels(BufferedImage img) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (gray(img, x, y) < 128) {
                    n++;
                }
            }
        }
        return n;
    }

    /** A page with a thick horizontal bar, standing in for a line of text. */
    private static BufferedImage withBar() {
        BufferedImage img = white(200, 100);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(20, 40, 160, 4);
        g.dispose();
        return img;
    }

    // ---------------------------------------------------------------- ImagePreprocessor

    @Test
    void despeckleRemovesIsolatedSpecksButKeepsThickStrokes() {
        BufferedImage img = withBar();
        long barPixels = darkPixels(img);
        Random random = new Random(1);
        for (int i = 0; i < 300; i++) { // isolated single-pixel specks away from the bar
            int x = random.nextInt(200);
            int y = random.nextInt(100);
            if (y < 30 || y > 55) {
                img.getRaster().setSample(x, y, 0, 0);
            }
        }
        assertThat(darkPixels(img)).isGreaterThan(barPixels + 100);

        BufferedImage cleaned = ImagePreprocessor.despeckle(img);

        long after = darkPixels(cleaned);
        assertThat(after).as("specks are gone").isBetween(barPixels - 10, barPixels + 10);
        assertThat(gray(cleaned, 100, 41)).as("the bar is still black").isZero();
    }

    @Test
    void despeckleReturnsANewImageAndLeavesTheInputAlone() {
        BufferedImage img = withBar();
        img.getRaster().setSample(5, 5, 0, 0);
        BufferedImage cleaned = ImagePreprocessor.despeckle(img);
        assertThat(cleaned).isNotSameAs(img);
        assertThat(cleaned.getWidth()).isEqualTo(200);
        assertThat(cleaned.getHeight()).isEqualTo(100);
        assertThat(gray(img, 5, 5)).as("input untouched").isZero();
        assertThat(gray(cleaned, 5, 5)).isEqualTo(255);
    }

    @Test
    void despeckleHandlesTinyImagesAndColourInput() {
        for (int size = 1; size <= 3; size++) {
            BufferedImage tiny = white(size, size);
            assertThat(ImagePreprocessor.despeckle(tiny).getWidth()).isEqualTo(size);
        }
        BufferedImage rgb = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 50, 50);
        g.dispose();
        BufferedImage cleaned = ImagePreprocessor.despeckle(rgb);
        assertThat(cleaned.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
        assertThat(gray(cleaned, 25, 25)).isEqualTo(255);
    }

    @Test
    void blankDetection() {
        assertThat(ImagePreprocessor.isBlank(white(200, 100))).isTrue();
        BufferedImage oneDot = white(200, 100);
        oneDot.getRaster().setSample(10, 10, 0, 0);
        assertThat(ImagePreprocessor.isBlank(oneDot)).as("one dark pixel is not content").isTrue();
        assertThat(ImagePreprocessor.isBlank(withBar())).isFalse();
        assertThat(ImagePreprocessor.isBlank(TestPdfs.bitmap(List.of("Some real text on the page")))).isFalse();
    }

    // ---------------------------------------------------------------- PreprocessingOcrEngine

    /** Records every image it is given and answers from a script. */
    private static final class Recorder implements OcrEngine {
        final List<BufferedImage> seen = new ArrayList<>();
        final List<String> answers;

        Recorder(String... answers) {
            this.answers = List.of(answers);
        }

        @Override
        public String recognize(BufferedImage page, int dpi) {
            seen.add(page);
            return answers.get(Math.min(seen.size() - 1, answers.size() - 1));
        }
    }

    @Test
    void offNeverTouchesTheImage() throws Exception {
        Recorder engine = new Recorder("");
        BufferedImage page = withBar();
        assertThat(new PreprocessingOcrEngine(engine, Options.Preprocess.OFF).recognize(page, 300)).isEmpty();
        assertThat(engine.seen).containsExactly(page);
    }

    @Test
    void autoDoesNotRetryWhenThePageAlreadyGaveUsefulText() throws Exception {
        Recorder engine = new Recorder("A perfectly good line of text");
        BufferedImage page = withBar();
        assertThat(new PreprocessingOcrEngine(engine, Options.Preprocess.AUTO).recognize(page, 300)).isEqualTo("A perfectly good line of text");
        assertThat(engine.seen).as("a clean scan costs exactly one OCR pass").containsExactly(page);
    }

    @Test
    void autoRetriesWithADespeckledImageWhenNothingWasFound() throws Exception {
        Recorder engine = new Recorder("", "Recovered text after cleaning");
        BufferedImage page = withBar();

        String text = new PreprocessingOcrEngine(engine, Options.Preprocess.AUTO).recognize(page, 300);

        assertThat(text).isEqualTo("Recovered text after cleaning");
        assertThat(engine.seen).hasSize(2);
        assertThat(engine.seen.get(0)).isSameAs(page);
        assertThat(engine.seen.get(1)).isNotSameAs(page);
    }

    @Test
    void autoKeepsTheFirstResultWhenTheRetryIsNoBetter() throws Exception {
        Recorder engine = new Recorder("ab", "");
        assertThat(new PreprocessingOcrEngine(engine, Options.Preprocess.AUTO).recognize(withBar(), 300)).isEqualTo("ab");
    }

    @Test
    void autoPicksTheRetryOnlyWhenItHasMoreText() throws Exception {
        Recorder engine = new Recorder("xyz", "xyz plus much more text");
        assertThat(new PreprocessingOcrEngine(engine, Options.Preprocess.AUTO).recognize(withBar(), 300)).isEqualTo("xyz plus much more text");
    }

    @Test
    void autoNeverRetriesABlankPage() throws Exception {
        Recorder engine = new Recorder("");
        BufferedImage blank = white(200, 100);
        assertThat(new PreprocessingOcrEngine(engine, Options.Preprocess.AUTO).recognize(blank, 300)).isEmpty();
        assertThat(engine.seen).as("blank pages must not cost a second pass").hasSize(1);
    }

    @Test
    void alwaysRecognisesOnlyTheCleanedImage() throws Exception {
        Recorder engine = new Recorder("cleaned result text");
        BufferedImage page = withBar();
        assertThat(new PreprocessingOcrEngine(engine, Options.Preprocess.ALWAYS).recognize(page, 300)).isEqualTo("cleaned result text");
        assertThat(engine.seen).hasSize(1);
        assertThat(engine.seen.get(0)).isNotSameAs(page);
    }

    @Test
    void usefulCharsCountsLettersAndDigitsOnly() {
        assertThat(PreprocessingOcrEngine.usefulChars(null)).isZero();
        assertThat(PreprocessingOcrEngine.usefulChars("  \n .,;-- ")).isZero();
        assertThat(PreprocessingOcrEngine.usefulChars("BP 120/80")).isEqualTo(7);
    }
}
