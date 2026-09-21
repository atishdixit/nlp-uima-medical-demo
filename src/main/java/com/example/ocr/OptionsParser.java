package com.example.ocr;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/** Parses command-line arguments. Both {@code --key value} and {@code --key=value} are accepted. */
public final class OptionsParser {

    /** Thrown for bad arguments; the message is shown to the user as is. */
    public static final class UsageException extends Exception {
        public UsageException(String message) {
            super(message);
        }
    }

    /** {@code --help} was requested. */
    public static final class HelpRequested extends Exception {
        public HelpRequested() {
            super("help requested");
        }
    }

    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z_]{2,20}(\\+[A-Za-z_]{2,20})*");

    private OptionsParser() {
    }

    public static final String USAGE = """
            Usage: java -jar pdf-ocr-extractor.jar [options]

            Reads every PDF in the input folder, runs OCR and writes one UTF-8 .txt file per PDF.

            Options (defaults in brackets):
              --input <dir>       folder with the PDFs                        [input_document]
              --output <dir>      folder for the .txt files                   [output_document]
              --tessdata <dir>    folder with the *.traineddata language files [tessdata]
              --lang <code>       OCR language, e.g. eng or eng+deu           [eng]
              --dpi <n>           render resolution, 72-600                   [300]
              --threads <n>       pages recognised in parallel, 1-32          [half the CPU cores, max 4]
              --mode <ocr|auto>   ocr = OCR every page; auto = use a page's own text if it has
                                  any and OCR only the rest                   [ocr]
              --preprocess <m>    noisy scans: auto = if a page comes back empty, clean the speckle noise
                                  and try again; off = never; always = clean every page first  [auto]
              --recursive         also process PDFs in sub-folders (folder layout is mirrored)
              --force             convert again even if an up-to-date .txt already exists
              --page-markers      put "=== Page N ===" lines between pages
              --help              show this text

            Exit code: 0 = all done, 1 = some files failed, 2 = could not start (bad options, no OCR engine).
            """;

    public static Options parse(String[] args, Path baseDir) throws UsageException, HelpRequested {
        Path input = baseDir.resolve("input_document");
        Path output = baseDir.resolve("output_document");
        Path tessdata = baseDir.resolve("tessdata");
        String language = "eng";
        int dpi = 300;
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        Options.Mode mode = Options.Mode.OCR;
        Options.Preprocess preprocess = Options.Preprocess.AUTO;
        boolean force = false;
        boolean recursive = false;
        boolean markers = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String key = arg;
            String inlineValue = null;
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 0) {
                key = arg.substring(0, eq);
                inlineValue = arg.substring(eq + 1);
            }
            switch (key) {
                case "--help", "-h", "/?" -> throw new HelpRequested();
                case "--force" -> force = flag(key, inlineValue);
                case "--recursive" -> recursive = flag(key, inlineValue);
                case "--page-markers" -> markers = flag(key, inlineValue);
                case "--input", "--output", "--tessdata", "--lang", "--dpi", "--threads", "--mode", "--preprocess" -> {
                    String value = inlineValue;
                    if (value == null) {
                        if (i + 1 >= args.length) {
                            throw new UsageException("Option " + key + " needs a value.");
                        }
                        value = args[++i];
                    }
                    if (value.isBlank()) {
                        throw new UsageException("Option " + key + " needs a non-empty value.");
                    }
                    switch (key) {
                        case "--input" -> input = baseDir.resolve(value);
                        case "--output" -> output = baseDir.resolve(value);
                        case "--tessdata" -> tessdata = baseDir.resolve(value);
                        case "--lang" -> language = language(value);
                        case "--dpi" -> dpi = number(key, value, Options.MIN_DPI, Options.MAX_DPI);
                        case "--threads" -> threads = number(key, value, 1, Options.MAX_THREADS);
                        case "--preprocess" -> preprocess = preprocess(value);
                        default -> mode = mode(value);
                    }
                }
                default -> throw new UsageException("Unknown option: " + arg);
            }
        }

        input = input.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        if (input.equals(output)) {
            throw new UsageException("The input and output folders must be different: " + input);
        }
        return new Options(input, output, tessdata.toAbsolutePath().normalize(), language, dpi, threads, mode, force, recursive, markers,
                preprocess);
    }

    private static boolean flag(String key, String inlineValue) throws UsageException {
        if (inlineValue == null) {
            return true;
        }
        return switch (inlineValue.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new UsageException("Option " + key + " is a switch and takes no value (or true/false).");
        };
    }

    private static int number(String key, String value, int min, int max) throws UsageException {
        try {
            int n = Integer.parseInt(value.trim());
            if (n < min || n > max) {
                throw new UsageException("Option " + key + " must be between " + min + " and " + max + " (got " + n + ").");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new UsageException("Option " + key + " must be a whole number (got '" + value + "').");
        }
    }

    private static String language(String value) throws UsageException {
        String v = value.trim();
        if (!LANGUAGE.matcher(v).matches()) {
            throw new UsageException("Option --lang must look like 'eng' or 'eng+deu' (got '" + value + "').");
        }
        return v;
    }

    private static Options.Preprocess preprocess(String value) throws UsageException {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> Options.Preprocess.OFF;
            case "auto" -> Options.Preprocess.AUTO;
            case "always" -> Options.Preprocess.ALWAYS;
            default -> throw new UsageException("Option --preprocess must be 'auto', 'off' or 'always' (got '" + value + "').");
        };
    }

    private static Options.Mode mode(String value) throws UsageException {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "ocr" -> Options.Mode.OCR;
            case "auto" -> Options.Mode.AUTO;
            default -> throw new UsageException("Option --mode must be 'ocr' or 'auto' (got '" + value + "').");
        };
    }
}
