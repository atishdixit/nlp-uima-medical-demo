package com.example.ocr;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** What a run did, for the final report and the exit code. */
public record Summary(List<FileResult> results, Duration elapsed, String fatalError) {

    public enum Status { CONVERTED, SKIPPED, FAILED }

    /** Outcome for one PDF. {@code message} explains a failure or a warning, and is null otherwise. */
    public record FileResult(Path pdf, Path output, Status status, int pages, int ocrPages, int embeddedPages,
                             int characters, String message, Duration elapsed) {
    }

    public static Summary fatal(String message, Duration elapsed) {
        return new Summary(List.of(), elapsed, message);
    }

    public long count(Status status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    public int totalPages() {
        return results.stream().filter(r -> r.status() == Status.CONVERTED).mapToInt(FileResult::pages).sum();
    }

    /** 0 = everything worked (or nothing to do), 1 = some files failed, 2 = the run could not start. */
    public int exitCode() {
        if (fatalError != null) {
            return 2;
        }
        return count(Status.FAILED) > 0 ? 1 : 0;
    }
}
