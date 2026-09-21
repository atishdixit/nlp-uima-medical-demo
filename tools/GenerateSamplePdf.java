import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/**
 * Makes input_document/sample_scanned_chart.pdf: a two-page "scanned" medical chart. Each page is only a bitmap
 * (no text layer), so the only way to get the words out is OCR. Run from the project folder (after building):
 *
 *     java -cp target\pdf-ocr-extractor.jar tools\GenerateSamplePdf.java
 */
public class GenerateSamplePdf {

    public static void main(String[] args) throws Exception {
        Path target = Path.of(args.length > 0 ? args[0] : "input_document/sample_scanned_chart.pdf");
        List<List<String>> pages = List.of(
                List.of("SAMPLE PROGRESS NOTE (scanned)",
                        "",
                        "Chief Complaint: Chest pain and cough for 2 days.",
                        "History: 57 year old male with hypertension and",
                        "type 2 diabetes mellitus.",
                        "",
                        "The patient denies fever, headache or nausea.",
                        "Pneumonia was ruled out by chest x-ray."),
                List.of("Vitals: BP 148/92 mmHg, HR 88, Temp 98.6",
                        "",
                        "Medications:",
                        "Metformin 500 mg twice daily",
                        "Lisinopril 10 mg once daily",
                        "",
                        "Plan: Increase metformin. Follow up in two weeks."));

        try (PDDocument doc = new PDDocument()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(LosslessFactory.createFromImage(doc, render(lines)), 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                }
            }
            doc.save(target.toFile());
        }
        System.out.println("Wrote " + target.toAbsolutePath());
    }

    /** A4 at 150 DPI, dark text on white. */
    private static BufferedImage render(List<String> lines) {
        BufferedImage img = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(20, 20, 20));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 34));
        int y = 160;
        for (String line : lines) {
            g.drawString(line, 100, y);
            y += 70;
        }
        g.dispose();
        return img;
    }
}
