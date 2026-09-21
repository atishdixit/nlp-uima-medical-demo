package com.example.ocr;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/** Small, dependency-free image clean-up used to rescue noisy scans. */
public final class ImagePreprocessor {

    private static final int DARK = 128;

    private ImagePreprocessor() {
    }

    /** An 8-bit grayscale copy (or the image itself when it already is one). */
    static BufferedImage toGray(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return source;
        }
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return gray;
    }

    /**
     * 3x3 median filter. It removes isolated speckles (dust, fax and photocopier noise, "salt and pepper") while keeping
     * strokes at least two pixels wide, which text is at OCR resolutions. Returns a new image; the input is not changed.
     */
    public static BufferedImage despeckle(BufferedImage source) {
        BufferedImage gray = toGray(source);
        int w = gray.getWidth();
        int h = gray.getHeight();
        byte[] in = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        byte[] out = ((DataBufferByte) result.getRaster().getDataBuffer()).getData();
        System.arraycopy(in, 0, out, 0, in.length); // the 1-pixel border stays as it is

        int[] window = new int[9];
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x;
                window[0] = in[i - w - 1] & 0xFF;
                window[1] = in[i - w] & 0xFF;
                window[2] = in[i - w + 1] & 0xFF;
                window[3] = in[i - 1] & 0xFF;
                window[4] = in[i] & 0xFF;
                window[5] = in[i + 1] & 0xFF;
                window[6] = in[i + w - 1] & 0xFF;
                window[7] = in[i + w] & 0xFF;
                window[8] = in[i + w + 1] & 0xFF;
                out[i] = (byte) median9(window);
            }
        }
        return result;
    }

    /** True when (almost) nothing on the page is dark, so there is no text to find and no point retrying. */
    public static boolean isBlank(BufferedImage source) {
        BufferedImage gray = toGray(source);
        byte[] data = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
        long dark = 0;
        for (byte b : data) {
            if ((b & 0xFF) < DARK) {
                dark++;
            }
        }
        return dark < data.length * 0.0002; // fewer than 0.02% dark pixels
    }

    /** Median of nine values by insertion sort; the array is reordered. */
    private static int median9(int[] a) {
        for (int i = 1; i < 9; i++) {
            int v = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > v) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = v;
        }
        return a[4];
    }
}
