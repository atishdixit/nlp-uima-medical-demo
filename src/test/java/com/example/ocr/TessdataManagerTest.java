package com.example.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Language data: never downloaded when present, downloaded safely when missing. Uses a local HTTP server, no internet. */
class TessdataManagerTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();
    private TessdataManager manager;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = files.get(exchange.getRequestURI().getPath().substring(1));
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        manager = new TessdataManager("http://127.0.0.1:" + server.getAddress().getPort() + "/models");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static byte[] model(int size) {
        return new byte[size];
    }

    @Test
    void doesNothingWhenTheLanguageDataIsAlreadyThere() throws IOException {
        Path tessdata = dir.resolve("tessdata");
        Files.createDirectories(tessdata);
        Files.write(tessdata.resolve("eng.traineddata"), model(200_000));

        manager.ensureLanguages(tessdata, "eng");

        assertThat(requests).hasValue(0);
    }

    @Test
    void downloadsMissingLanguagesAndCreatesTheFolder() throws IOException {
        files.put("models/eng.traineddata", model(150_000));
        files.put("models/deu.traineddata", model(160_000));
        Path tessdata = dir.resolve("new/tessdata");

        manager.ensureLanguages(tessdata, "eng+deu");

        assertThat(tessdata.resolve("eng.traineddata")).hasSize(150_000);
        assertThat(tessdata.resolve("deu.traineddata")).hasSize(160_000);
        assertThat(tessdata.resolve("eng.traineddata.part")).doesNotExist();
        assertThat(requests).hasValue(2);
    }

    @Test
    void onlyFetchesTheLanguagesThatAreMissing() throws IOException {
        Path tessdata = dir.resolve("tessdata");
        Files.createDirectories(tessdata);
        Files.write(tessdata.resolve("eng.traineddata"), model(200_000));
        files.put("models/fra.traineddata", model(150_000));

        manager.ensureLanguages(tessdata, "eng+fra");

        assertThat(requests).hasValue(1);
        assertThat(tessdata.resolve("fra.traineddata")).exists();
    }

    @Test
    void anUnknownLanguageFailsWithAHelpfulMessageAndLeavesNothingBehind() {
        Path tessdata = dir.resolve("tessdata");
        assertThatThrownBy(() -> manager.ensureLanguages(tessdata, "xyz"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Could not download")
                .hasMessageContaining("'xyz'")
                .hasMessageContaining("HTTP 404")
                .hasMessageContaining("xyz.traineddata");
        assertThat(tessdata.resolve("xyz.traineddata")).doesNotExist();
        assertThat(tessdata.resolve("xyz.traineddata.part")).doesNotExist();
    }

    @Test
    void anErrorPageOrTruncatedDownloadIsRejected() {
        files.put("models/eng.traineddata", "<html>Not Found</html>".getBytes());
        Path tessdata = dir.resolve("tessdata");
        assertThatThrownBy(() -> manager.ensureLanguages(tessdata, "eng"))
                .isInstanceOf(IOException.class).hasMessageContaining("only 22 bytes");
        assertThat(tessdata.resolve("eng.traineddata")).doesNotExist();
        assertThat(tessdata.resolve("eng.traineddata.part")).doesNotExist();
    }

    @Test
    void aCorruptExistingFileIsReplaced() throws IOException {
        Path tessdata = dir.resolve("tessdata");
        Files.createDirectories(tessdata);
        Files.writeString(tessdata.resolve("eng.traineddata"), "corrupt");
        files.put("models/eng.traineddata", model(180_000));

        manager.ensureLanguages(tessdata, "eng");

        assertThat(tessdata.resolve("eng.traineddata")).hasSize(180_000);
    }

    @Test
    void anUnreachableServerGivesAClearError() {
        server.stop(0);
        Path tessdata = dir.resolve("tessdata");
        assertThatThrownBy(() -> manager.ensureLanguages(tessdata, "eng"))
                .isInstanceOf(IOException.class).hasMessageContaining("Could not download").hasMessageContaining("manually");
    }
}
