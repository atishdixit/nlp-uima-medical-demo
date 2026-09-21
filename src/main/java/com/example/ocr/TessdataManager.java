package com.example.ocr;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes sure the Tesseract language data ({@code <lang>.traineddata}) is present, downloading any that is
 * missing. English ships with the project, so the normal case needs no network at all.
 */
public final class TessdataManager {

    public static final String DEFAULT_BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/";

    /** Real models are megabytes; anything tiny is an error page or a truncated download. */
    static final long MIN_PLAUSIBLE_BYTES = 100_000;

    private static final Logger log = LoggerFactory.getLogger(TessdataManager.class);

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public TessdataManager() {
        this(DEFAULT_BASE_URL);
    }

    public TessdataManager(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /** @param language one code or several joined with '+', e.g. {@code eng+deu} */
    public void ensureLanguages(Path tessdataDir, String language) throws IOException {
        Files.createDirectories(tessdataDir);
        for (String code : language.split("\\+")) {
            Path file = tessdataDir.resolve(code + ".traineddata");
            if (Files.isRegularFile(file) && Files.size(file) >= MIN_PLAUSIBLE_BYTES) {
                continue;
            }
            download(code, file);
        }
    }

    private void download(String code, Path target) throws IOException {
        URI uri = URI.create(baseUrl + code + ".traineddata");
        log.info("Language data '{}' not found in {}; downloading {}", code, target.getParent(), uri);
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try {
            HttpResponse<InputStream> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(temp);
            if (size < MIN_PLAUSIBLE_BYTES) {
                throw new IOException("the downloaded file is only " + size + " bytes");
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded {} ({} KB)", target.getFileName(), size / 1024);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw new IOException("Could not download the OCR language data '" + code + "' from " + uri + " (" + e.getMessage()
                    + "). Download it manually and save it as " + target, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(temp);
            throw new IOException("Interrupted while downloading the OCR language data '" + code + "'", e);
        }
    }
}
