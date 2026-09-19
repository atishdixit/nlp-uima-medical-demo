package com.example.mednlp.uima.annotators;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the sample charts from samples/ (copied onto the test classpath by the build). */
public final class SampleCharts {

    private SampleCharts() {
    }

    public static String read(String name) throws IOException {
        try (InputStream in = SampleCharts.class.getClassLoader().getResourceAsStream("samples/" + name)) {
            if (in == null) {
                throw new IOException("Sample chart not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
