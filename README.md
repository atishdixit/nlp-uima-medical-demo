# PDF OCR Extractor

A small stand-alone Java program. It reads every PDF in **`input_document\`**, runs **OCR** on the pages, and writes one
UTF-8 text file per PDF into **`output_document\`** (`report.pdf` → `report.txt`).

```
input_document\   scan1.pdf  scan2.pdf        run.bat         output_document\   scan1.txt  scan2.txt
        └────────────────────────────►  java -jar (OCR)  ────────────────────────────►
```

Java 21 · Maven · Apache PDFBox (renders the pages) · Tesseract 5 via Tess4J (the OCR engine, **bundled in the jar**).

## Quick start

1. Install **JDK 21 or newer** (`winget install EclipseAdoptium.Temurin.21.JDK`, then open a new terminal; check `java -version`).
2. Put your PDFs in `input_document\`.
3. Run:

```bat
run.bat
```

That is all. The first run builds the jar (needs internet once, about a minute); later runs start in about a second.
A demo file, `input_document\sample_scanned_chart.pdf` (a scanned two-page medical note), is included so you can see it work
straight away. Delete it when you add your own files.

```
10:15:02 INFO  Found 1 PDF file(s) in ...\input_document
10:15:03 INFO  [1/1] sample_scanned_chart.pdf -> sample_scanned_chart.txt (2 pages, 410 characters, 1.4 s)
10:15:03 INFO  Converted: 1   Skipped (up to date): 0   Failed: 0   Pages: 2   Time: 1.9 s
```

## What you need

| Requirement | Notes |
|-------------|-------|
| **JDK 21+** | The only thing you install. Maven is not needed: the Maven Wrapper (`mvnw.cmd`) is included and downloads it when building. |
| **Windows x64** | The bundled OCR native libraries (Tesseract 5.5, Leptonica) are for 64-bit Windows. They need the **Microsoft Visual C++ Redistributable 2015-2022 (x64)**, which almost every PC already has ([download](https://aka.ms/vs/17/release/vc_redist.x64.exe) if you get a "native library could not be loaded" message). |
| **Language data** | `tessdata\eng.traineddata` (English, 4 MB) is included, so English works offline. Other languages are downloaded automatically the first time you use them (see `--lang`). |
| Internet | Only for the first build, and only when you ask for a new language. |

No separate Tesseract installation is required.

## Options

Anything you type after `run.bat` goes to the program:

| Option | Default | Meaning |
|--------|---------|---------|
| `--input <dir>` | `input_document` | Folder with the PDFs |
| `--output <dir>` | `output_document` | Folder for the `.txt` files (created if missing) |
| `--lang <code>` | `eng` | OCR language, e.g. `eng`, `deu`, `eng+deu`. The model is downloaded on first use. |
| `--mode ocr\|auto` | `ocr` | `ocr` = OCR every page. `auto` = use a page's own text layer when it has one and OCR only the rest (faster and exact for digital PDFs; mixed files work too) |
| `--dpi <72-600>` | `300` | Resolution the pages are rendered at. 300 is the usual sweet spot; raise it for tiny print. |
| `--threads <1-32>` | half the cores, max 4 | Pages recognised in parallel |
| `--preprocess auto\|off\|always` | `auto` | Noisy scans: `auto` retries a page that came back empty after removing speckle noise; `always` cleans every page first (slower); `off` never |
| `--recursive` | off | Also convert PDFs in sub-folders; the folder layout is mirrored in the output |
| `--force` | off | Convert again even if an up-to-date `.txt` exists |
| `--page-markers` | off | Put `=== Page N ===` lines between pages |
| `--tessdata <dir>` | `tessdata` | Folder with the `*.traineddata` files |
| `--help` | | Show all options |

Examples:

```bat
run.bat --mode auto                        rem digital PDFs: exact text, no OCR needed
run.bat --lang eng+deu --page-markers      rem English and German, pages labelled
run.bat --input D:\scans --output D:\text --recursive
run.bat --force                            rem redo everything
set JAVA_OPTS=-Xmx4g && run.bat            rem more memory for very large scans
```

Other scripts: `build.bat` rebuilds the jar after you change the source (`build.bat skiptests` skips the tests).
You can also call the jar yourself: `java -jar target\pdf-ocr-extractor.jar --help`.

## How it behaves

- **Safe to re-run.** A PDF whose `.txt` is already newer than the PDF is skipped; changed PDFs are converted again.
- **One bad file never stops the rest.** Corrupt, empty or password-protected PDFs are reported (with the reason) and the other files continue. Nothing is written for a failed file, and it is retried next run.
- **No half-written output.** Text is written to a temporary file and moved into place.
- **Page order is always right**, even though pages are recognised in parallel.
- **Memory stays bounded.** Only a few rendered pages exist at a time, big PDFs are read through temp files, and poster-sized pages are rendered at a lower resolution instead of exhausting memory.
- **Blank pages** give empty text (no error). A file where nothing at all was recognised is written empty and flagged with a WARNING.
- **Noisy scans.** Clean pages are read as they are. If a page that is not blank comes back (almost) empty, it is despeckled (3×3 median filter) and read again; this rescues dust/fax-style noise that otherwise defeats Tesseract entirely.
- **Text is tidied, not altered:** line endings unified, trailing spaces and runs of blank lines removed. Words are never changed.
- **Log:** the console shows progress; `logs\pdf-ocr.log` keeps the full history.

### Exit codes (for scripts)

| Code | Meaning |
|------|---------|
| 0 | Everything converted or skipped (or nothing to do) |
| 1 | Finished, but some files failed |
| 2 | Could not start: bad options, no Java, OCR library or language data unavailable |

## Accuracy: what to expect

Measured with generated test pages (real Tesseract, the shipped English model):

| Page | Result |
|------|--------|
| Clean scan, printed text | Exact, including numbers such as `148/92`, `98.6`, `500 mg` |
| Skewed up to 3°, low contrast, or light noise | Exact |
| Heavy speckle noise | Recovered by `--preprocess auto` (returned nothing without it) |
| Heavy noise **and** low contrast | Readable with a few character errors ("Chiat" for "Chest") |

Tips for better results: scan at 300 DPI or more, black and white or grayscale, straight, with good contrast. Handwriting
is not supported (Tesseract is for printed text). Tables and multi-column layouts are read in reading order, not as a grid.
For higher accuracy replace `tessdata\eng.traineddata` with the larger `eng.traineddata` from
<https://github.com/tesseract-ocr/tessdata_best> (about 4× slower).

## Project layout

```
run.bat  build.bat  pom.xml  mvnw.cmd
input_document\      your PDFs (a demo PDF is included)
output_document\     the text files
tessdata\            OCR language data (eng.traineddata included)
logs\                pdf-ocr.log (created on first run)
tools\GenerateSamplePdf.java     makes the demo scanned PDF
src\main\java\com\example\ocr
  PdfOcrApplication   entry point, exit codes, summary
  OptionsParser       command line
  BatchRunner         folders, skipping, failure isolation, atomic writes
  PdfConverter        render pages (PDFBox), recognise in parallel, keep order
  OcrEngine / TesseractOcrEngine / PreprocessingOcrEngine / ImagePreprocessor
  TessdataManager     language data check and download
  PdfScanner  TextCleaner  Summary
src\test\java        80 tests
```

## Tests

`build.bat` runs them; or `mvnw.cmd test`. **80 tests**, about 30 seconds: the command line, folder scanning, text cleaning,
language download (against a local test server, no internet), the batch behaviour (skipping, failures, recursion, exit
codes), the converter (ordering under parallelism, digital / scanned / mixed pages, password-protected, corrupt, huge
pages), the noise filter and retry logic, and end-to-end runs with the **real Tesseract engine** on generated scanned PDFs
(including the noisy one).

## Troubleshooting

| Message / symptom | Fix |
|-------------------|-----|
| `Java 21 or newer is required` | Install JDK 21, open a **new** terminal, check `java -version`. |
| `The Tesseract native library could not be loaded` | Install the Visual C++ Redistributable (x64), link above. 32-bit Java or ARM Windows is not supported. |
| `Could not download the OCR language data 'xyz'` | The code is wrong or you are offline. Save `xyz.traineddata` from <https://github.com/tesseract-ocr/tessdata_fast> into `tessdata\`. |
| `The PDF is password-protected` | Remove the password (open it, print to PDF) and try again. |
| `No text was recognised` warning | The pages are blank or too poor to read. Try `--dpi 400`, `--preprocess always`, or a better scan. |
| `Out of memory` | Lower `--threads` or `--dpi`, or `set JAVA_OPTS=-Xmx4g`. |
| A file is skipped but you want it redone | `--force`, or delete its `.txt`. |
| The `.txt` looks wrong in an old editor | It is UTF-8 without BOM; open it with a UTF-8-aware editor (Notepad in Windows 10+ does). |

## Using the text with the medical chart NLP demo

The `.txt` files are plain text, so they can be fed straight into the sibling project (`nlp-demo-uima`):

```bat
curl -X POST "http://localhost:8080/api/v1/charts/analyze/text" -H "Content-Type: text/plain; charset=UTF-8" --data-binary @output_document\sample_scanned_chart.txt
```

## Licences

All components are free and open source: Tesseract and Tess4J, Apache PDFBox, Leptonica (BSD), the `tessdata_fast`
models (Apache 2.0) and logback (EPL/LGPL). The Java code in this folder is yours to use as you like.
