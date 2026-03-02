package com.zplviewer.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Local ZPL II renderer.
 * Uses Java AWT for text/graphics and ZXing (open source) for barcodes.
 *
 * Supported commands:
 *   ^XA / ^XZ  – label start/end
 *   ^FO         – field origin
 *   ^A0         – scaleable font (height, width, orientation)
 *   ^CF         – change default font
 *   ^FD / ^FS   – field data / field separator
 *   ^BY         – barcode defaults (module width, height)
 *   ^BC         – Code 128
 *   ^B3         – Code 39
 *   ^BQ         – QR Code
 *   ^GB         – graphic box / line
 *   ^LH / ^PW / ^LL – label home / print width / label length (parsed, not resized)
 */
public class ZplRenderer {

    private final int labelWidthDots;
    private final int labelHeightDots;

    // ── current field state ──────────────────────────────
    private int fieldOriginX = 0;
    private int fieldOriginY = 0;

    // ── font state ───────────────────────────────────────
    private int    fontHeight      = 30;
    private char   fontOrientation = 'N';

    // ── barcode defaults (^BY) ───────────────────────────
    private int barcodeModuleWidth = 2;
    private int barcodeHeight      = 100;

    // ── pending barcode (set by ^BC / ^B3 / ^BQ, consumed by ^FD) ───
    private String   pendingBarcodeType   = null;
    private String[] pendingBarcodeParams = null;

    private Graphics2D g;

    // ─────────────────────────────────────────────────────────────────
    public ZplRenderer(int labelWidthDots, int labelHeightDots) {
        this.labelWidthDots  = Math.max(labelWidthDots,  50);
        this.labelHeightDots = Math.max(labelHeightDots, 50);
    }

    /** Render ZPL string and return PNG bytes. */
    public byte[] renderToPng(String zpl) throws Exception {
        BufferedImage image = new BufferedImage(
                labelWidthDots, labelHeightDots, BufferedImage.TYPE_INT_RGB);

        g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, labelWidthDots, labelHeightDots);
        g.setColor(Color.BLACK);

        parse(zpl);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Parser
    // ─────────────────────────────────────────────────────────────────

    private void parse(String zpl) {
        int i = 0;
        while (i < zpl.length()) {
            char c = zpl.charAt(i);
            if (c != '^' && c != '~') { i++; continue; }
            if (c == '~') { i++; continue; }          // skip ~ commands

            i++;                                       // skip '^'
            if (i >= zpl.length()) break;

            // Read 2-char command code
            int cmdEnd = Math.min(i + 2, zpl.length());
            String cmd = zpl.substring(i, cmdEnd).toUpperCase();
            i = cmdEnd;

            if ("FD".equals(cmd)) {
                // Data ends at ^FS (or end of string)
                int fsIdx = findCaret(zpl, i, "FS");
                String data = (fsIdx < 0) ? zpl.substring(i) : zpl.substring(i, fsIdx);
                i = (fsIdx < 0) ? zpl.length() : fsIdx + 3;   // +3 to skip ^FS
                onFieldData(data);
            } else {
                // Params: everything until next ^ or ~
                int end = i;
                while (end < zpl.length() && zpl.charAt(end) != '^' && zpl.charAt(end) != '~') end++;
                String params = zpl.substring(i, end).trim().replaceAll("[\r\n\t]", "");
                i = end;
                onCommand(cmd, params);
            }
        }
    }

    /** Return index of '^' before 'cmd' starting from 'from', or -1. */
    private int findCaret(String zpl, int from, String cmd) {
        for (int i = from; i < zpl.length() - 2; i++) {
            if (zpl.charAt(i) == '^' && zpl.substring(i + 1, i + 3).equalsIgnoreCase(cmd))
                return i;
        }
        return -1;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Command dispatcher
    // ─────────────────────────────────────────────────────────────────

    private void onCommand(String cmd, String params) {
        String[] p = params.split(",", -1);

        switch (cmd) {
            case "XA" -> resetState();
            case "XZ" -> { /* end of label */ }

            // Field origin
            case "FO" -> { fieldOriginX = intAt(p, 0, 0); fieldOriginY = intAt(p, 1, 0); }
            case "FT" -> { fieldOriginX = intAt(p, 0, fieldOriginX); fieldOriginY = intAt(p, 1, fieldOriginY); }

            // Font
            case "A0","A1","A2","A3","A4","A5","A6","A7","A8","A9" -> {
                // ^A0{orientation},{height},{width}
                if (p.length > 0 && !p[0].isEmpty()) fontOrientation = p[0].toUpperCase().charAt(0);
                fontHeight = intAt(p, 1, fontHeight);
            }
            case "CF" -> fontHeight = intAt(p, 1, fontHeight);  // ^CF{font},{height},{width}

            // Barcode defaults
            case "BY" -> {
                barcodeModuleWidth = intAt(p, 0, barcodeModuleWidth);
                barcodeHeight      = intAt(p, 2, barcodeHeight);
            }

            // Barcode types – set pending, consumed when ^FD arrives
            case "BC" -> { pendingBarcodeType = "BC"; pendingBarcodeParams = p; }  // Code 128
            case "B3" -> { pendingBarcodeType = "B3"; pendingBarcodeParams = p; }  // Code 39
            case "BQ" -> { pendingBarcodeType = "BQ"; pendingBarcodeParams = p; }  // QR Code
            case "BE" -> { pendingBarcodeType = "BE"; pendingBarcodeParams = p; }  // EAN-13
            case "BU" -> { pendingBarcodeType = "BU"; pendingBarcodeParams = p; }  // UPC-A

            // Graphic box: ^GB{width},{height},{thickness},{color},{rounding}
            case "GB" -> {
                int w     = intAt(p, 0, 1);
                int h     = intAt(p, 1, 1);
                int thick = intAt(p, 2, 1);
                Color col = (p.length > 3 && "W".equalsIgnoreCase(p[3].trim())) ? Color.WHITE : Color.BLACK;
                int rnd   = intAt(p, 4, 0);
                drawBox(w, h, thick, col, rnd);
            }

            // Field separator – clear pending barcode
            case "FS" -> { pendingBarcodeType = null; pendingBarcodeParams = null; }

            // Informational – no rendering needed
            case "LH","PW","LL","PR","PQ","CI","PM","MN","MT","MD","MMT","JMA","JMB" -> { /* ignore */ }
        }
    }

    private void resetState() {
        fieldOriginX       = 0;
        fieldOriginY       = 0;
        fontHeight         = 30;
        fontOrientation    = 'N';
        barcodeModuleWidth = 2;
        barcodeHeight      = 100;
        pendingBarcodeType = null;
        pendingBarcodeParams = null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Field data handler
    // ─────────────────────────────────────────────────────────────────

    private void onFieldData(String data) {
        try {
            if (pendingBarcodeType != null) {
                renderBarcode(data);
            } else {
                renderText(data);
            }
        } catch (Exception ex) {
            renderText("[Err: " + ex.getMessage() + "]");
        }
        pendingBarcodeType   = null;
        pendingBarcodeParams = null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Text rendering
    // ─────────────────────────────────────────────────────────────────

    private void renderText(String text) {
        int fh = Math.max(fontHeight, 8);
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, fh);
        g.setFont(font);
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();

        AffineTransform saved = g.getTransform();

        switch (fontOrientation) {
            case 'R' -> {  // 90° clockwise
                g.translate(fieldOriginX + fh, fieldOriginY);
                g.rotate(Math.PI / 2);
                g.drawString(text, 0, fm.getAscent());
            }
            case 'I' -> {  // 180°
                g.translate(fieldOriginX + fm.stringWidth(text), fieldOriginY + fh);
                g.rotate(Math.PI);
                g.drawString(text, 0, fm.getAscent());
            }
            case 'B' -> {  // 270° (bottom-up)
                g.translate(fieldOriginX, fieldOriginY + fm.stringWidth(text));
                g.rotate(-Math.PI / 2);
                g.drawString(text, 0, fm.getAscent());
            }
            default -> {   // 'N' – normal
                g.drawString(text, fieldOriginX, fieldOriginY + fm.getAscent());
            }
        }

        g.setTransform(saved);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Barcode routing
    // ─────────────────────────────────────────────────────────────────

    private void renderBarcode(String data) throws Exception {
        switch (pendingBarcodeType) {
            case "BC"       -> renderCode128(data);
            case "B3"       -> renderCode39(data);
            case "BQ"       -> renderQRCode(data);
            case "BE","BU"  -> renderEAN(data);
            default         -> renderCode128(data);
        }
    }

    // ── Code 128 ─────────────────────────────────────────────────────

    private void renderCode128(String data) throws Exception {
        int bh = (pendingBarcodeParams != null) ? intAt(pendingBarcodeParams, 1, barcodeHeight) : barcodeHeight;
        boolean printText = (pendingBarcodeParams == null)
                || !"N".equalsIgnoreCase(safeGet(pendingBarcodeParams, 2, "Y"));

        // Estimate width: (modules * moduleWidth); Code128 ~11 bars/char + 35 overhead
        int estWidth = Math.max((data.length() * 11 + 45) * barcodeModuleWidth, 100);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);

        BitMatrix matrix = new Code128Writer().encode(data, BarcodeFormat.CODE_128, estWidth, bh, hints);
        BufferedImage barImg = MatrixToImageWriter.toBufferedImage(matrix);
        g.drawImage(barImg, fieldOriginX, fieldOriginY, null);

        if (printText) {
            int textSize = Math.max(14, barcodeModuleWidth * 3);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textSize));
            FontMetrics fm = g.getFontMetrics();
            int tx = fieldOriginX + (barImg.getWidth() - fm.stringWidth(data)) / 2;
            g.setColor(Color.BLACK);
            g.drawString(data, Math.max(tx, fieldOriginX), fieldOriginY + bh + fm.getAscent() + 2);
        }
    }

    // ── Code 39 ──────────────────────────────────────────────────────

    private void renderCode39(String data) throws Exception {
        int bh = (pendingBarcodeParams != null) ? intAt(pendingBarcodeParams, 1, barcodeHeight) : barcodeHeight;
        int estWidth = Math.max((data.length() * 16 + 30) * barcodeModuleWidth, 100);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);

        BitMatrix matrix = new Code39Writer().encode(data, BarcodeFormat.CODE_39, estWidth, bh, hints);
        g.drawImage(MatrixToImageWriter.toBufferedImage(matrix), fieldOriginX, fieldOriginY, null);
    }

    // ── QR Code ──────────────────────────────────────────────────────

    private void renderQRCode(String data) throws Exception {
        int magnification = (pendingBarcodeParams != null) ? intAt(pendingBarcodeParams, 2, 3) : 3;
        int size = Math.max(magnification * 20, 80);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix matrix = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints);
        g.drawImage(MatrixToImageWriter.toBufferedImage(matrix), fieldOriginX, fieldOriginY, null);
    }

    // ── EAN-13 / UPC-A (fallback to Code 128) ────────────────────────

    private void renderEAN(String data) throws Exception {
        // Use Code 128 as a safe fallback for EAN/UPC
        int bh = (pendingBarcodeParams != null) ? intAt(pendingBarcodeParams, 1, barcodeHeight) : barcodeHeight;
        int estWidth = Math.max((data.length() * 11 + 45) * barcodeModuleWidth, 100);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);

        BitMatrix matrix = new Code128Writer().encode(data, BarcodeFormat.CODE_128, estWidth, bh, hints);
        g.drawImage(MatrixToImageWriter.toBufferedImage(matrix), fieldOriginX, fieldOriginY, null);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Graphic box
    // ─────────────────────────────────────────────────────────────────

    private void drawBox(int width, int height, int thickness, Color color, int rounding) {
        g.setColor(color);
        Stroke savedStroke = g.getStroke();
        boolean filled = (thickness * 2 >= Math.min(width, height));

        if (rounding > 0) {
            int arc = rounding * Math.min(width, height) / 8;
            if (filled) g.fillRoundRect(fieldOriginX, fieldOriginY, width, height, arc, arc);
            else {
                g.setStroke(new BasicStroke(thickness));
                g.drawRoundRect(fieldOriginX, fieldOriginY, width, height, arc, arc);
            }
        } else {
            if (filled) g.fillRect(fieldOriginX, fieldOriginY, width, height);
            else {
                g.setStroke(new BasicStroke(thickness));
                g.drawRect(fieldOriginX, fieldOriginY, width, height);
            }
        }

        g.setStroke(savedStroke);
        g.setColor(Color.BLACK);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────

    private int intAt(String[] arr, int idx, int def) {
        if (arr == null || idx >= arr.length || arr[idx] == null) return def;
        try { return Integer.parseInt(arr[idx].trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private String safeGet(String[] arr, int idx, String def) {
        if (arr == null || idx >= arr.length || arr[idx] == null) return def;
        String v = arr[idx].trim();
        return v.isEmpty() ? def : v;
    }
}
