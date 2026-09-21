package com.example.ocr;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/** OCR with Tesseract through Tess4J (the native Tesseract libraries are bundled in the jar). */
public final class TesseractOcrEngine implements OcrEngine {

    private static final int OEM_LSTM_ONLY = 1;
    private static final int PSM_AUTO = 3;

    private final Tesseract tesseract = new Tesseract();

    public TesseractOcrEngine(Path tessdataDir, String language) {
        tesseract.setDatapath(tessdataDir.toAbsolutePath().toString());
        tesseract.setLanguage(language);
        tesseract.setOcrEngineMode(OEM_LSTM_ONLY); // the downloaded *_fast models are LSTM-only
        tesseract.setPageSegMode(PSM_AUTO);
        tesseract.setVariable("preserve_interword_spaces", "1");
    }

    @Override
    public String recognize(BufferedImage page, int dpi) throws OcrException {
        try {
            tesseract.setVariable("user_defined_dpi", Integer.toString(dpi));
            return tesseract.doOCR(page);
        } catch (TesseractException e) {
            throw new OcrException("Tesseract failed: " + e.getMessage(), e);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new OcrException("The Tesseract native library could not be loaded ("
                    + e.getMessage() + "). This program needs 64-bit Windows and the Microsoft Visual C++ "
                    + "Redistributable 2015-2022 (x64): https://aka.ms/vs/17/release/vc_redist.x64.exe", e);
        }
    }
}
