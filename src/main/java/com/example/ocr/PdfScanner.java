package com.example.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Finds the PDFs to convert. */
public final class PdfScanner {

    private PdfScanner() {
    }

    /** All regular files ending in .pdf (any case), sorted so runs are reproducible. Hidden files are ignored. */
    public static List<Path> findPdfs(Path inputDir, boolean recursive) throws IOException {
        try (Stream<Path> stream = recursive ? Files.walk(inputDir) : Files.list(inputDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .filter(p -> !isHidden(p))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isHidden(Path p) {
        try {
            return p.getFileName().toString().startsWith(".") || Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }

    /** {@code input/sub/report.PDF} becomes {@code output/sub/report.txt} (folder layout is mirrored). */
    public static Path outputFor(Path inputDir, Path outputDir, Path pdf) {
        Path relative = inputDir.relativize(pdf);
        String name = relative.getFileName().toString();
        String base = name.substring(0, name.length() - ".pdf".length());
        Path target = outputDir.resolve(relative);
        return target.resolveSibling(base + ".txt");
    }
}
