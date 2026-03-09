package com.zplviewer.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 從 ZPL 參考 PNG 量測每個字元的寬度，輸出 CG_WIDTHS 查表。
 *
 * 使用方式（在 backend 目錄執行）：
 *   mvn exec:java -Dexec.mainClass="com.zplviewer.util.CharWidthMeasurer" \
 *                 -Dexec.args="../image/font_reference.png"
 *
 * 測試用 ZPL：
 *   ^XA
 *   ^FO30,110^A0N,30,24^FD(A0N)ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789^FS
 *   ^FO30,150^A0N,30,24^FD(A0N)abcdefghijklmnopqrstuvwxyz-0123456789^FS
 *   ^XZ
 */
public class CharWidthMeasurer {

    // ── ZPL 參數 ──────────────────────────────────────────────────────
    static final String LINE1   = "(A0N)ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789";
    static final String LINE2   = "(A0N)abcdefghijklmnopqrstuvwxyz-0123456789";
    static final int    FO_X    = 30;    // ^FO x
    static final int    LINE1_Y = 110;   // ^FO y（第一行）
    static final int    LINE2_Y = 150;   // ^FO y（第二行）
    static final int    FONT_H  = 30;    // ^A0N,30,24 → height
    static final int    FONT_W  = 24;    // ^A0N,30,24 → width
    static final int    LABEL_W_DOTS = 813; // 4" × 8dpmm

    // ── 黑色像素判定閾值 ──────────────────────────────────────────────
    static final int BLACK_THR = 100;

    // ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "../image/font_reference.png";
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) { System.err.println("無法讀取圖片: " + path); return; }

        // 自動偵測 pixel-per-dot 縮放比例
        double scale = (double) img.getWidth() / LABEL_W_DOTS;
        System.out.printf("圖片: %s%n", path);
        System.out.printf("大小: %d × %d px，自動偵測縮放比: %.3f (%.1f px/dot)%n%n",
                img.getWidth(), img.getHeight(), scale, scale);

        int foX    = (int) Math.round(FO_X    * scale);
        int line1Y = (int) Math.round(LINE1_Y * scale);
        int line2Y = (int) Math.round(LINE2_Y * scale);
        int fontH  = (int) Math.round(FONT_H  * scale);

        System.out.println("══════════════════════════════════════════");
        System.out.println(" Line 1（大寫）");
        System.out.println("══════════════════════════════════════════");
        MeasureResult r1 = measureLine(img, LINE1, foX, line1Y, fontH, scale);

        System.out.println();
        System.out.println("══════════════════════════════════════════");
        System.out.println(" Line 2（小寫）");
        System.out.println("══════════════════════════════════════════");
        MeasureResult r2 = measureLine(img, LINE2, foX, line2Y, fontH, scale);

        // ── 整體摘要 ─────────────────────────────────────────────────
        System.out.println();
        System.out.println("══════════════════════════════════════════");
        System.out.println(" 整體摘要");
        System.out.println("══════════════════════════════════════════");
        System.out.printf("fontHeight=%d  fontWidth=%d  w/h=%.4f%n", FONT_H, FONT_W, (double) FONT_W / FONT_H);
        System.out.printf("Line1 實際寬 %d dots，平均每字 %.2f dots%n",
                r1.totalDots, r1.avgDots);
        System.out.printf("Line2 實際寬 %d dots，平均每字 %.2f dots%n",
                r2.totalDots, r2.avgDots);

        if (r1.charDots != null && r2.charDots != null) {
            System.out.println();
            System.out.println("══════════════════════════════════════════");
            System.out.println(" 請將下列程式碼貼入 ZplRenderer.java");
            System.out.println("══════════════════════════════════════════");
            printJavaTable(LINE1, r1.charDots, LINE2, r2.charDots);
        } else {
            System.out.println();
            System.out.println("⚠ 字元分割未完全成功，請參考上方 segment 分析。");
            System.out.printf("  建議先用全局 xScale = %.4f 修正整體寬度。%n",
                    r1.avgDots / (FONT_H * (double) FONT_W / FONT_H));
        }
    }

    // ─────────────────────────────────────────────────────────────────

    static MeasureResult measureLine(BufferedImage img, String text,
                                     int xStart, int yTop, int fontH, double scale) {
        int yBottom = Math.min(yTop + fontH + (int)(2 * scale), img.getHeight() - 1);
        int yPad    = Math.max(0, yTop - (int)(2 * scale));

        // 1. 找最右側黑像素 → 字串總寬
        int textEnd = xStart;
        for (int x = xStart; x < Math.min(xStart + (int)(1800 * scale), img.getWidth()); x++) {
            if (hasBlack(img, x, yPad, yBottom)) textEnd = x;
        }
        int totalPx   = textEnd - xStart + 1;
        int totalDots = (int) Math.round(totalPx / scale);
        double avgDots = (double) totalDots / text.length();

        System.out.printf("字串: \"%s\"%n", text);
        System.out.printf("x 範圍: [%d, %d] px，總寬: %d px = %d dots，平均: %.2f dots/char%n",
                xStart, textEnd, totalPx, totalDots, avgDots);

        // 2. 逐欄掃描，建立「有無黑點」陣列
        boolean[] black = new boolean[img.getWidth()];
        for (int x = xStart; x <= textEnd; x++) black[x] = hasBlack(img, x, yPad, yBottom);

        // 3. Gap-based 字元分割
        List<int[]> segs = findSegments(black, xStart, textEnd);
        System.out.printf("偵測到 %d 個 segment（預期 %d 個字元）%n", segs.size(), text.length());

        if (segs.size() == text.length()) {
            System.out.println();
            System.out.println("字元   adv_px  adv_dots  ratio  (ink_px ink_dots)");
            System.out.println("------ ------  --------  -----  -----------------");
            int[] charDots = new int[text.length()];
            for (int i = 0; i < text.length(); i++) {
                // advance width = distance from this char's start to next char's start
                // (last char uses ink width as fallback)
                int advPx = (i + 1 < segs.size())
                        ? segs.get(i + 1)[0] - segs.get(i)[0]
                        : segs.get(i)[1] - segs.get(i)[0] + 1;
                int inkPx = segs.get(i)[1] - segs.get(i)[0] + 1;
                int d  = (int) Math.round(advPx / scale);
                charDots[i] = d;
                System.out.printf(" %-4s  %6d  %8d  %.4f  (%6d %8d)%n",
                        display(text.charAt(i)), advPx, d, (float) d / FONT_H,
                        inkPx, (int) Math.round(inkPx / scale));
            }
            return new MeasureResult(totalDots, avgDots, charDots);
        } else {
            // 輸出 segment 供人工分析
            System.out.println("（分割數不符，輸出原始 segments 供參考）");
            for (int i = 0; i < segs.size(); i++) {
                int px = segs.get(i)[1] - segs.get(i)[0] + 1;
                System.out.printf("  seg[%02d] x=[%d,%d] px=%d dots=%.1f%n",
                        i, segs.get(i)[0], segs.get(i)[1], px, px / scale);
            }
            return new MeasureResult(totalDots, avgDots, null);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────

    static boolean hasBlack(BufferedImage img, int x, int y0, int y1) {
        if (x < 0 || x >= img.getWidth()) return false;
        for (int y = y0; y <= y1; y++) {
            if (y < 0 || y >= img.getHeight()) continue;
            int rgb = img.getRGB(x, y);
            if (((rgb >> 16) & 0xFF) < BLACK_THR
                    && ((rgb >> 8) & 0xFF) < BLACK_THR
                    && (rgb & 0xFF) < BLACK_THR) return true;
        }
        return false;
    }

    static List<int[]> findSegments(boolean[] black, int from, int to) {
        List<int[]> segs = new ArrayList<>();
        int start = -1;
        for (int x = from; x <= to + 1; x++) {
            boolean b = (x <= to) && black[x];
            if (start == -1 && b)  { start = x; }
            else if (start != -1 && !b) { segs.add(new int[]{start, x - 1}); start = -1; }
        }
        return segs;
    }

    static String display(char c) {
        if (c == '\\') return "\\\\";
        return String.valueOf(c);
    }

    static String javaLiteral(char c) {
        if (c == '\'') return "\\'";
        if (c == '\\') return "\\\\";
        return String.valueOf(c);
    }

    static void printJavaTable(String line1, int[] dots1, String line2, int[] dots2) {
        System.out.println("static final Map<Character, Float> CG_WIDTHS = new HashMap<>();");
        System.out.println("static {");
        System.out.println("    // Measured from ZPL reference PNG (^A0N," + FONT_H + "," + FONT_W + ")");

        printChars(line1, dots1);
        printChars(line2, dots2);

        System.out.println("}");
    }

    static void printChars(String text, int[] dots) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float ratio = (float) dots[i] / FONT_H;
            System.out.printf("    CG_WIDTHS.put('%s', %.4ff);  // %d dots%n",
                    javaLiteral(c), ratio, dots[i]);
        }
    }

    record MeasureResult(int totalDots, double avgDots, int[] charDots) {}
}
