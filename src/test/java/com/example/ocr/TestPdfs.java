package com.example.ocr;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Builds the PDFs the tests need. Everything is generated, so no binary fixtures are checked in. */
final class TestPdfs {

    private TestPdfs() {
    }

    /** A "scanned" PDF: every page is only a bitmap of the text, with no text layer, so it can only be read by OCR. */
    static Path scanned(Path file, List<String>... pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (List<String> lines : pages) {
                addScannedPage(doc, PDRectangle.A4, lines);
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /** A digital PDF with a real text layer (Helvetica). */
    static Path digital(Path file, String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String text : pageTexts) {
                addTextPage(doc, PDRectangle.A4, text);
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /** Pages of different sizes, so a test can tell pages apart from the rendered image width alone. */
    static Path digitalWithDistinctPageSizes(Path file, int pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                addTextPage(doc, new PDRectangle(300 + 20 * i, 400), "Page number " + (i + 1) + " has some text on it");
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /** One digital page followed by one scanned page. */
    static Path mixed(Path file, String digitalText, List<String> scannedLines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            addTextPage(doc, PDRectangle.A4, digitalText);
            addScannedPage(doc, PDRectangle.A4, scannedLines);
            doc.save(file.toFile());
        }
        return file;
    }

    static Path blank(Path file, int pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /**
     * A scan with heavy salt-and-pepper speckle (like dust or fax noise): {@code specks} random mid-gray pixels on a page of
     * small serif text. Plain Tesseract returns nothing for this; it is the case the pre-processing exists for.
     */
    static Path scannedWithSpeckle(Path file, List<String> lines, int specks) throws IOException {
        BufferedImage img = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(20, 20, 30));
            g.setFont(new Font(Font.SERIF, Font.PLAIN, 30));
            int y = 200;
            for (String line : lines) {
                g.drawString(line, 100, y);
                y += 60;
            }
        } finally {
            g.dispose();
        }
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < specks; i++) {
            int v = 120 + random.nextInt(100);
            img.setRGB(random.nextInt(img.getWidth()), random.nextInt(img.getHeight()), new Color(v, v, v).getRGB());
        }
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, img), 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
            }
            doc.save(file.toFile());
        }
        return file;
    }

    static Path noPages(Path file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.save(file.toFile());
        }
        return file;
    }

    /** Needs a password to open. */
    static Path passwordProtected(Path file, String password) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            addTextPage(doc, PDRectangle.A4, "secret text that needs a password to read");
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(file.toFile());
        }
        return file;
    }

    static Path notAPdf(Path file) throws IOException {
        Files.writeString(file, "This is definitely not a PDF file, just some text pretending to be one.");
        return file;
    }

    static Path truncated(Path source, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length / 3));
        return file;
    }

    private static void addTextPage(PDDocument doc, PDRectangle size, String text) throws IOException {
        PDPage page = new PDPage(size);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
            cs.newLineAtOffset(30, size.getHeight() - 60);
            cs.showText(text);
            cs.endText();
        }
    }

    private static void addScannedPage(PDDocument doc, PDRectangle size, List<String> lines) throws IOException {
        PDPage page = new PDPage(size);
        doc.addPage(page);
        PDImageXObject image = LosslessFactory.createFromImage(doc, bitmap(lines));
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(image, 0, 0, size.getWidth(), size.getHeight());
        }
    }

    /** An A4 page at 150 DPI with the lines drawn in large black text on white, like a clean scan. */
    static BufferedImage bitmap(List<String> lines) {
        BufferedImage img = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 46));
            int y = 200;
            for (String line : lines) {
                g.drawString(line, 100, y);
                y += 100;
            }
        } finally {
            g.dispose();
        }
        return img;
    }
}
