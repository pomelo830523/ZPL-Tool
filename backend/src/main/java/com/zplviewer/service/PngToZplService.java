package com.zplviewer.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;

@Service
public class PngToZplService {

    // ── Inner records ──────────────────────────────────────────────────
    private record DetectedBarcode(int x, int y, int w, int h, String type, String data) {}
    private record DetectedShape(int x, int y, int w, int h, int thickness, boolean filled) {}
    private record DetectedText(int x, int y, int w, int h, String text, int fontHeight) {}

    // ═════════════════════════════════════════════════════════════════
    //  Public entry point
    // ═════════════════════════════════════════════════════════════════

    public record ConversionResult(String zpl, BufferedImage overlayImage) {}

    public ConversionResult convert(byte[] pngBytes, int threshold, int minShapeDots, String tessDataPath)
            throws Exception {

        BufferedImage src = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (src == null) throw new IllegalArgumentException("Cannot read image");

        int W = src.getWidth();
        int H = src.getHeight();

        // 1. Binarize
        boolean[][] binary = binarize(src, threshold);

        // Used-mask: true = already claimed by a detected element
        boolean[][] used = new boolean[H][W];

        List<Object> elements = new ArrayList<>();

        // 2. Barcode detection
        List<DetectedBarcode> barcodes = detectBarcodes(src);
        for (DetectedBarcode b : barcodes) {
            maskRegion(used, b.x(), b.y(), b.w(), b.h(), H, W);
            elements.add(b);
        }

        // 3. Shape detection on residual
        boolean[][] residualForShapes = applyMask(binary, used, H, W);
        List<DetectedShape> shapes = detectShapes(residualForShapes, H, W, minShapeDots);
        for (DetectedShape s : shapes) {
            maskRegion(used, s.x(), s.y(), s.w(), s.h(), H, W);
            elements.add(s);
        }

        // 4. OCR text detection
        List<DetectedText> texts = new ArrayList<>();
        if (tessDataPath != null && !tessDataPath.isBlank()) {
            boolean[][] residualForOcr = applyMask(binary, used, H, W);
            BufferedImage maskedImg = toBufferedImage(residualForOcr, W, H);
            texts = detectText(maskedImg, tessDataPath);
            for (DetectedText t : texts) {
                maskRegion(used, t.x(), t.y(), t.w(), t.h(), H, W);
                elements.add(t);
            }
        }

        // 5. Residual → ~DG
        boolean[][] residual = applyMask(binary, used, H, W);
        String grfHex = encodeGrf(residual, W, H);
        int bytesPerRow = (W + 7) / 8;
        int totalBytes = bytesPerRow * H;

        // Assemble ZPL
        String zpl = assembleZpl(elements, grfHex, totalBytes, bytesPerRow);

        // Build overlay preview
        BufferedImage overlay = buildOverlay(src, barcodes, shapes, texts, residual);

        return new ConversionResult(zpl, overlay);
    }

    // ═════════════════════════════════════════════════════════════════
    //  1. Binarize
    // ═════════════════════════════════════════════════════════════════

    private boolean[][] binarize(BufferedImage src, int threshold) {
        int W = src.getWidth();
        int H = src.getHeight();
        boolean[][] out = new boolean[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r * 77 + g * 150 + b * 29) >> 8; // fast luminance
                out[y][x] = (gray < threshold); // true = black
            }
        }
        return out;
    }

    // ═════════════════════════════════════════════════════════════════
    //  2. Barcode detection via ZXing
    // ═════════════════════════════════════════════════════════════════

    private List<DetectedBarcode> detectBarcodes(BufferedImage img) {
        List<DetectedBarcode> result = new ArrayList<>();
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.PDF_417
            ));
            Result decoded = new MultiFormatReader().decode(bitmap, hints);
            if (decoded != null) {
                com.google.zxing.ResultPoint[] pts = decoded.getResultPoints();
                int minX = img.getWidth(), minY = img.getHeight(), maxX = 0, maxY = 0;
                for (com.google.zxing.ResultPoint pt : pts) {
                    minX = Math.min(minX, (int) pt.getX());
                    minY = Math.min(minY, (int) pt.getY());
                    maxX = Math.max(maxX, (int) pt.getX());
                    maxY = Math.max(maxY, (int) pt.getY());
                }
                // Add padding
                int pad = 10;
                minX = Math.max(0, minX - pad);
                minY = Math.max(0, minY - pad);
                maxX = Math.min(img.getWidth() - 1, maxX + pad);
                maxY = Math.min(img.getHeight() - 1, maxY + pad);
                int w = maxX - minX;
                int h = maxY - minY;
                if (w > 0 && h > 0) {
                    String type = mapBarcodeType(decoded.getBarcodeFormat());
                    result.add(new DetectedBarcode(minX, minY, w, h, type, decoded.getText()));
                }
            }
        } catch (NotFoundException ignored) {
            // No barcode found
        } catch (Exception e) {
            // Other ZXing errors — skip silently
        }
        return result;
    }

    private String mapBarcodeType(BarcodeFormat fmt) {
        return switch (fmt) {
            case CODE_128 -> "BC";
            case CODE_39  -> "B3";
            case QR_CODE  -> "BQ";
            default       -> "BC";
        };
    }

    // ═════════════════════════════════════════════════════════════════
    //  3. Shape detection (connected components + rectangle check)
    // ═════════════════════════════════════════════════════════════════

    private List<DetectedShape> detectShapes(boolean[][] binary, int H, int W, int minArea) {
        List<DetectedShape> result = new ArrayList<>();
        boolean[][] visited = new boolean[H][W];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!binary[y][x] || visited[y][x]) continue;

                // Flood-fill to find connected component
                List<int[]> component = new ArrayList<>();
                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                visited[y][x] = true;
                int minCx = x, minCy = y, maxCx = x, maxCy = y;

                while (!queue.isEmpty()) {
                    int[] cur = queue.poll();
                    int cx = cur[0], cy = cur[1];
                    component.add(cur);
                    minCx = Math.min(minCx, cx); minCy = Math.min(minCy, cy);
                    maxCx = Math.max(maxCx, cx); maxCy = Math.max(maxCy, cy);

                    int[][] neighbors = {{cx-1,cy},{cx+1,cy},{cx,cy-1},{cx,cy+1}};
                    for (int[] nb : neighbors) {
                        int nx = nb[0], ny = nb[1];
                        if (nx >= 0 && nx < W && ny >= 0 && ny < H
                                && binary[ny][nx] && !visited[ny][nx]) {
                            visited[ny][nx] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }

                if (component.size() < minArea) continue;

                int bw = maxCx - minCx + 1;
                int bh = maxCy - minCy + 1;

                DetectedShape shape = classifyShape(binary, minCx, minCy, bw, bh, H, W);
                if (shape != null) {
                    result.add(shape);
                }
            }
        }
        return result;
    }

    private DetectedShape classifyShape(boolean[][] binary, int x, int y, int w, int h, int H, int W) {
        if (w <= 0 || h <= 0) return null;

        int totalPixels = w * h;
        int blackCount = 0;
        int edgeCount = 0;

        for (int row = y; row < y + h && row < H; row++) {
            for (int col = x; col < x + w && col < W; col++) {
                if (binary[row][col]) {
                    blackCount++;
                    boolean onEdge = (row == y || row == y + h - 1 || col == x || col == x + w - 1);
                    if (onEdge) edgeCount++;
                }
            }
        }

        double fillRatio = (double) blackCount / totalPixels;
        int perimeter = 2 * (w + h) - 4;
        double edgeRatio = perimeter > 0 ? (double) edgeCount / perimeter : 0;
        double interiorDensity = totalPixels > 0
                ? (double)(blackCount - edgeCount) / ((w - 2) * (h - 2) + 1)
                : 0;

        // Line: extreme aspect ratio
        if ((double) w / h >= 8 || (double) h / w >= 8) {
            int thickness = Math.min(w, h);
            return new DetectedShape(x, y, w, h, Math.max(1, thickness), false);
        }

        // Filled rectangle
        if (fillRatio > 0.80) {
            int thick = Math.max(w, h);
            return new DetectedShape(x, y, w, h, thick, true);
        }

        // Hollow border rectangle
        if (edgeRatio > 0.60 && interiorDensity < 0.20) {
            int thickness = estimateThickness(binary, x, y, w, h, H, W);
            return new DetectedShape(x, y, w, h, thickness, false);
        }

        return null; // Not rectangle-like → residual
    }

    private int estimateThickness(boolean[][] binary, int x, int y, int w, int h, int H, int W) {
        int maxInset = Math.min(w, h) / 2;
        for (int inset = 1; inset <= maxInset; inset++) {
            int innerBlack = 0;
            int innerTotal = 0;
            int x2 = x + inset, y2 = y + inset;
            int w2 = w - 2 * inset, h2 = h - 2 * inset;
            if (w2 <= 0 || h2 <= 0) return inset;
            for (int row = y2; row < y2 + h2 && row < H; row++) {
                for (int col = x2; col < x2 + w2 && col < W; col++) {
                    if (binary[row][col]) innerBlack++;
                    innerTotal++;
                }
            }
            if (innerTotal > 0 && (double) innerBlack / innerTotal < 0.15) {
                return inset;
            }
        }
        return 1;
    }

    // ═════════════════════════════════════════════════════════════════
    //  4. OCR text detection via Tess4J
    // ═════════════════════════════════════════════════════════════════

    private List<DetectedText> detectText(BufferedImage img, String tessDataPath) {
        List<DetectedText> result = new ArrayList<>();
        try {
            Tesseract tess = new Tesseract();
            tess.setDatapath(tessDataPath);
            tess.setLanguage("eng");
            tess.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);

            List<Word> words = tess.getWords(img, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            for (Word word : words) {
                String text = word.getText().trim();
                if (text.isEmpty()) continue;
                Rectangle r = word.getBoundingBox();
                if (r.width <= 0 || r.height <= 0) continue;
                int fontH = Math.max(8, r.height);
                result.add(new DetectedText(r.x, r.y, r.width, r.height, text, fontH));
            }
        } catch (Exception e) {
            // Tess4J unavailable or error → skip OCR silently
        }
        return result;
    }

    // ═════════════════════════════════════════════════════════════════
    //  5. Encode residual as ~DG hex
    // ═════════════════════════════════════════════════════════════════

    private String encodeGrf(boolean[][] pixels, int W, int H) {
        int bytesPerRow = (W + 7) / 8;
        StringBuilder sb = new StringBuilder(bytesPerRow * H * 2);
        for (int y = 0; y < H; y++) {
            for (int bx = 0; bx < bytesPerRow; bx++) {
                int byteVal = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int px = bx * 8 + bit;
                    if (px < W && pixels[y][px]) {
                        byteVal |= (0x80 >> bit);
                    }
                }
                sb.append(String.format("%02X", byteVal));
            }
        }
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════
    //  6. Assemble final ZPL
    // ═════════════════════════════════════════════════════════════════

    private String assembleZpl(List<Object> elements, String grfHex, int totalBytes, int bytesPerRow) {
        StringBuilder sb = new StringBuilder();

        // ~DG preamble (stored outside ^XA/^XZ)
        sb.append("~DGR:ZPLV0001.GRF,")
          .append(totalBytes).append(",")
          .append(bytesPerRow).append(",")
          .append(grfHex).append("\n");

        sb.append("^XA\n");

        for (Object el : elements) {
            if (el instanceof DetectedBarcode b) {
                switch (b.type()) {
                    case "BC" -> sb.append(String.format(
                            "^FO%d,%d^BY2^BCN,100,Y,N,N^FD%s^FS\n",
                            b.x(), b.y(), b.data()));
                    case "B3" -> sb.append(String.format(
                            "^FO%d,%d^BY1^B3N,%d,Y,N^FD%s^FS\n",
                            b.x(), b.y(), b.h(), b.data()));
                    case "BQ" -> sb.append(String.format(
                            "^FO%d,%d^BQN,2,5^FD%s^FS\n",
                            b.x(), b.y(), b.data()));
                    default -> sb.append(String.format(
                            "^FO%d,%d^BY2^BCN,100,Y,N,N^FD%s^FS\n",
                            b.x(), b.y(), b.data()));
                }
            } else if (el instanceof DetectedShape s) {
                sb.append(String.format("^FO%d,%d^GB%d,%d,%d^FS\n",
                        s.x(), s.y(), s.w(), s.h(), s.thickness()));
            } else if (el instanceof DetectedText t) {
                sb.append(String.format("^FO%d,%d^A0N,%d,%d^FD%s^FS\n",
                        t.x(), t.y(), t.fontHeight(), (int)(t.fontHeight() * 0.8), t.text()));
            }
        }

        // Residual graphic recall at (0,0)
        sb.append("^FO0,0^XGR:ZPLV0001.GRF,1,1^FS\n");
        sb.append("^XZ\n");

        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════

    private void maskRegion(boolean[][] used, int x, int y, int w, int h, int H, int W) {
        for (int row = y; row < y + h && row < H; row++) {
            for (int col = x; col < x + w && col < W; col++) {
                if (row >= 0 && col >= 0) used[row][col] = true;
            }
        }
    }

    private boolean[][] applyMask(boolean[][] binary, boolean[][] used, int H, int W) {
        boolean[][] out = new boolean[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                out[y][x] = binary[y][x] && !used[y][x];
            }
        }
        return out;
    }

    private BufferedImage toBufferedImage(boolean[][] pixels, int W, int H) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.setRGB(x, y, pixels[y][x] ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return img;
    }

    /** Build color-coded detection overlay image. */
    private BufferedImage buildOverlay(BufferedImage src,
                                       List<DetectedBarcode> barcodes,
                                       List<DetectedShape> shapes,
                                       List<DetectedText> texts,
                                       boolean[][] residual) {
        BufferedImage overlay = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = overlay.createGraphics();
        g.drawImage(src, 0, 0, null);

        Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 3}, 0);
        g.setStroke(dashed);

        // Barcodes: cyan
        for (DetectedBarcode b : barcodes) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g.setColor(new Color(0, 200, 220));
            g.fillRect(b.x(), b.y(), b.w(), b.h());
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(0, 180, 200));
            g.drawRect(b.x(), b.y(), b.w(), b.h());
        }

        // Shapes: green
        for (DetectedShape s : shapes) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f));
            g.setColor(new Color(30, 160, 60));
            g.fillRect(s.x(), s.y(), s.w(), s.h());
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(20, 140, 40));
            g.drawRect(s.x(), s.y(), s.w(), s.h());
        }

        // Text: orange
        for (DetectedText t : texts) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f));
            g.setColor(new Color(230, 120, 0));
            g.fillRect(t.x(), t.y(), t.w(), t.h());
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(200, 100, 0));
            g.drawRect(t.x(), t.y(), t.w(), t.h());
        }

        g.setComposite(AlphaComposite.SrcOver);
        g.dispose();
        return overlay;
    }

    /** Encode BufferedImage (ARGB or RGB) to PNG base64. */
    public String toPngBase64(BufferedImage img) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
