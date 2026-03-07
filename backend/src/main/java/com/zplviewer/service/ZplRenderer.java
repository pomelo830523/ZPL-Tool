package com.zplviewer.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.zplviewer.model.RenderWarning;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 本地 ZPL II 渲染器：Java AWT（文字/圖形）+ ZXing（條碼）
 *
 * 生命週期：render() → analyze() → applyDebugOverlay()（選用）→ toPng()
 */
public class ZplRenderer {

    // ── Label dimensions ─────────────────────────────────────────────
    private final int labelWidthDots;
    private final int labelHeightDots;

    // ── Resolution & detection params ────────────────────────────────
    private final int dpmm;               // dots per mm
    private final int defaultBarcodeHeight; // default ^BY barcode height
    private final int minBarcodeGapDots;  // minimum horizontal gap between barcodes
    private final int marginDots;         // 3 mm safety margin from each edge

    // ── Current field state ──────────────────────────────────────────
    private int  fieldOriginX    = 0;
    private int  fieldOriginY    = 0;

    // ── Font state ───────────────────────────────────────────────────
    private char fontFace        = '0';  // '0' = CG Triumvirate (bold proportional); 'A'-'Z' = bitmap fonts
    private int  fontHeight      = 30;
    private int  fontWidth       = 0;    // 0 = unspecified → use natural width
    private char fontOrientation = 'N';

    // ── Field modifiers ──────────────────────────────────────────────
    private boolean fieldReverse = false;  // ^FR: invert foreground/background

    // ── Barcode defaults (^BY) ───────────────────────────────────────
    private int  barcodeModuleWidth = 2;
    private int  barcodeHeight;           // initialised from defaultBarcodeHeight

    // ── Pending barcode (set by ^BC/^B3/^BQ/^BX, consumed by ^FD) ───
    private String   pendingBarcodeType   = null;
    private String[] pendingBarcodeParams = null;

    // ── BoundingBox tracking ─────────────────────────────────────────
    /**
     * 每個渲染欄位的邊界記錄。
     * Java record：x/y = 左上角，w/h = 寬高，type/label 用於警告說明。
     */
    private record FieldRecord(int x, int y, int w, int h, String type, String label) {}
    private final List<FieldRecord> renderedFields = new ArrayList<>();

    // ── Graphics store (~DG / ^XG) ───────────────────────────────────
    private final Map<String, BufferedImage> graphicStore = new HashMap<>();

    // ── Embedded font (^A0 / Font 0 = CG Triumvirate) ────────────────
    private final Font cgFont;

    // ── Syntax warning collection ─────────────────────────────────────
    private final List<RenderWarning> syntaxWarnings = new ArrayList<>();
    private int currentLine = 1;
    private final java.util.Set<String> warnedCmds = new java.util.HashSet<>();

    /** ZPL 指令中可安全忽略（不影響渲染）的指令集合。 */
    private static final java.util.Set<String> KNOWN_SILENT_CMDS = java.util.Set.of(
        "LH","PW","LL","CI","PQ","JN","JZ","LS","LT","PM","PO","PR",
        "MN","MT","MM","MD","MF","FP","HV","HH","SN","LR","SP","CC","CD",
        "ZZ","SZ","FN","ZD","JA","JB","JC","JD","JE","JF","JG","JH","JI",
        "JJ","JK","JL","JM","JO","JP","JQ","JR","JS","JT","JU","JV","JW",
        "JX","JY","MC","ML","MG","HL","IL","ID","KD","KV","LA","LF","LO","LP",
        "FV","DF","QA","QE","QI","QQ","QR"
    );

    /** 不支援但常見、會影響渲染的指令及其說明。 */
    private static final java.util.Map<String, String> UNSUPPORTED_CMD_MSGS;
    static {
        UNSUPPORTED_CMD_MSGS = new java.util.HashMap<>();
        UNSUPPORTED_CMD_MSGS.put("GF", "內嵌點陣圖形（^GF），圖形將不顯示");
        UNSUPPORTED_CMD_MSGS.put("FH", "Hex 欄位資料編碼（^FH），特殊字元可能顯示錯誤");
        UNSUPPORTED_CMD_MSGS.put("GC", "繪製圓形（^GC），將被略過");
        UNSUPPORTED_CMD_MSGS.put("GD", "繪製對角線（^GD），將被略過");
        UNSUPPORTED_CMD_MSGS.put("GE", "繪製橢圓形（^GE），將被略過");
        UNSUPPORTED_CMD_MSGS.put("GS", "更改字型大小（^GS），將被略過");
    }

    // ── Graphics state ───────────────────────────────────────────────
    private BufferedImage image;
    private Graphics2D    g;

    // ─────────────────────────────────────────────────────────────────
    public ZplRenderer(int labelWidthDots, int labelHeightDots,
                       int dpmm, int defaultBarcodeHeight, int minBarcodeGapDots,
                       Font cgFont) {
        this.labelWidthDots    = Math.max(labelWidthDots,  50);
        this.labelHeightDots   = Math.max(labelHeightDots, 50);
        this.dpmm              = Math.max(dpmm, 1);
        this.defaultBarcodeHeight = Math.max(defaultBarcodeHeight, 10);
        this.minBarcodeGapDots = Math.max(minBarcodeGapDots, 0);
        this.marginDots        = (int) Math.round(3.0 * this.dpmm);
        this.barcodeHeight     = this.defaultBarcodeHeight;
        this.cgFont            = cgFont;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Step 1 — 渲染 ZPL
    // ═════════════════════════════════════════════════════════════════

    public void render(String zpl) {
        image = new BufferedImage(labelWidthDots, labelHeightDots, BufferedImage.TYPE_INT_RGB);
        g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, labelWidthDots, labelHeightDots);
        g.setColor(Color.BLACK);

        parse(zpl);
    }

    // ═════════════════════════════════════════════════════════════════
    //  Step 2 — 分析警告
    // ═════════════════════════════════════════════════════════════════

    /**
     * 分析所有已渲染欄位，回傳問題清單。
     *
     * @param overlapThresholdMm 重疊閾值（mm）：交集的寬 AND 高都超過此值才視為重疊
     */
    /** 加入一個語法警告（參數錯誤類，不做 dedup）。 */
    private void addSyntaxWarn(String cmd, String detail) {
        RenderWarning w = new RenderWarning();
        w.setType("SYNTAX");
        w.setCommand(cmd);
        w.setLine(currentLine);
        w.setFieldA("^" + cmd + "（第 " + currentLine + " 行）");
        w.setDetail(detail);
        syntaxWarnings.add(w);
    }

    /** 加入一個未知/不支援指令警告（以指令名稱 dedup）。 */
    private void addUnknownCmdWarn(String cmd, String detail) {
        if (warnedCmds.add(cmd)) {
            RenderWarning w = new RenderWarning();
            w.setType("SYNTAX");
            w.setCommand(cmd);
            w.setLine(currentLine);
            w.setFieldA("^" + cmd + "（第 " + currentLine + " 行）");
            w.setDetail(detail);
            syntaxWarnings.add(w);
        }
    }

    public List<RenderWarning> analyze(double overlapThresholdMm) {
        int overlapThresholdDots = (int) Math.round(overlapThresholdMm * dpmm);
        List<RenderWarning> warnings = new ArrayList<>(syntaxWarnings);  // 語法警告優先
        checkOutOfBounds(warnings);
        checkOverlaps(warnings, Math.max(0, overlapThresholdDots));
        checkBarcodeGap(warnings);
        return warnings;
    }

    private void checkOutOfBounds(List<RenderWarning> warnings) {
        // 安全邊界：距離標籤邊緣 3 mm 以內視為超出
        int safeL = marginDots;
        int safeT = marginDots;
        int safeR = labelWidthDots  - marginDots;
        int safeB = labelHeightDots - marginDots;

        for (FieldRecord f : renderedFields) {
            List<String> sides = new ArrayList<>();
            int excessDots = 0;

            if (f.x()          < safeL) { sides.add("LEFT");   excessDots = Math.max(excessDots, safeL - f.x()); }
            if (f.y()          < safeT) { sides.add("TOP");    excessDots = Math.max(excessDots, safeT - f.y()); }
            if (f.x() + f.w() > safeR) { sides.add("RIGHT");  excessDots = Math.max(excessDots, f.x() + f.w() - safeR); }
            if (f.y() + f.h() > safeB) { sides.add("BOTTOM"); excessDots = Math.max(excessDots, f.y() + f.h() - safeB); }

            if (!sides.isEmpty()) {
                RenderWarning w = new RenderWarning();
                w.setType("OUT_OF_BOUNDS");
                w.setFieldA(f.type() + ": " + f.label());
                w.setSides(String.join("+", sides));
                w.setExcessDots(excessDots);
                w.setDetail(String.join("、", sides)
                        + " 超出安全邊界 " + String.format("%.1f", (double) excessDots / dpmm) + " mm（安全距離 3 mm）");
                w.setBoundsA(new int[]{f.x(), f.y(), f.w(), f.h()});
                warnings.add(w);
            }
        }
    }

    private void checkOverlaps(List<RenderWarning> warnings, int threshold) {
        for (int i = 0; i < renderedFields.size(); i++) {
            for (int j = i + 1; j < renderedFields.size(); j++) {
                FieldRecord a = renderedFields.get(i);
                FieldRecord b = renderedFields.get(j);

                int ix = Math.max(a.x(), b.x());
                int iy = Math.max(a.y(), b.y());
                int iw = Math.min(a.x() + a.w(), b.x() + b.w()) - ix;
                int ih = Math.min(a.y() + a.h(), b.y() + b.h()) - iy;

                // 兩個維度都必須超過閾值才視為真正重疊
                if (iw > threshold && ih > threshold) {
                    RenderWarning w = new RenderWarning();
                    w.setType("OVERLAP");
                    w.setFieldA(a.type() + ": " + a.label());
                    w.setFieldB(b.type() + ": " + b.label());
                    w.setDetail("重疊區域 " + String.format("%.1f", (double) iw / dpmm)
                            + "×" + String.format("%.1f", (double) ih / dpmm) + " mm");
                    w.setBoundsA(new int[]{a.x(), a.y(), a.w(), a.h()});
                    w.setBoundsB(new int[]{b.x(), b.y(), b.w(), b.h()});
                    w.setIntersect(new int[]{ix, iy, iw, ih});
                    warnings.add(w);
                }
            }
        }
    }

    private static final java.util.Set<String> BARCODE_TYPES =
            java.util.Set.of("CODE128", "CODE39", "QRCODE", "DATAMATRIX");

    // ── CG Triumvirate Bold advance widths ────────────────────────────
    // 量測來源：Labelary 輸出（^A0N,30,24）
    // 使用方式：charWidth = CG_WIDTHS[c] * effectiveW / CG_REF_RATIO
    //   effectiveW = fontWidth（若已指定）否則 = fontHeight
    //   CG_REF_RATIO = 量測時的 w/h = 24/30
    private static final float CG_REF_RATIO    = 24.0f / 30.0f;  // 0.8
    private static final float CG_DEFAULT_RATIO = 0.3333f;        // 未量測字元的預設值
    private static final java.util.Map<Character, Float> CG_WIDTHS = new java.util.HashMap<>();
    static {
        // ── 大寫 A–Z（advance width，從 Labelary PNG 量測）──────────────
        CG_WIDTHS.put('A', 0.4667f);  CG_WIDTHS.put('B', 0.4000f);
        CG_WIDTHS.put('C', 0.4667f);  CG_WIDTHS.put('D', 0.4667f);
        CG_WIDTHS.put('E', 0.4000f);  CG_WIDTHS.put('F', 0.3667f);
        CG_WIDTHS.put('G', 0.5000f);  CG_WIDTHS.put('H', 0.5000f);
        CG_WIDTHS.put('I', 0.2667f);  CG_WIDTHS.put('J', 0.4000f);
        CG_WIDTHS.put('K', 0.4333f);  CG_WIDTHS.put('L', 0.4000f);
        CG_WIDTHS.put('M', 0.6000f);  CG_WIDTHS.put('N', 0.4667f);
        CG_WIDTHS.put('O', 0.4667f);  CG_WIDTHS.put('P', 0.4333f);
        CG_WIDTHS.put('Q', 0.4667f);  CG_WIDTHS.put('R', 0.4333f);
        CG_WIDTHS.put('S', 0.4333f);  CG_WIDTHS.put('T', 0.4333f);
        CG_WIDTHS.put('U', 0.4667f);  CG_WIDTHS.put('V', 0.4333f);
        CG_WIDTHS.put('W', 0.6333f);  CG_WIDTHS.put('X', 0.4333f);
        CG_WIDTHS.put('Y', 0.4667f);  CG_WIDTHS.put('Z', 0.4333f);
        // ── 小寫 a–z ──────────────────────────────
        CG_WIDTHS.put('a', 0.4000f);  CG_WIDTHS.put('b', 0.3667f);
        CG_WIDTHS.put('c', 0.3667f);  CG_WIDTHS.put('d', 0.3667f);
        CG_WIDTHS.put('e', 0.4000f);  CG_WIDTHS.put('f', 0.2667f);
        CG_WIDTHS.put('g', 0.4000f);  CG_WIDTHS.put('h', 0.4000f);
        CG_WIDTHS.put('i', 0.2667f);  CG_WIDTHS.put('j', 0.2667f);
        CG_WIDTHS.put('k', 0.3667f);  CG_WIDTHS.put('l', 0.2000f);
        CG_WIDTHS.put('m', 0.6333f);  CG_WIDTHS.put('n', 0.4333f);
        CG_WIDTHS.put('o', 0.4333f);  CG_WIDTHS.put('p', 0.4000f);
        CG_WIDTHS.put('q', 0.4000f);  CG_WIDTHS.put('r', 0.3333f);
        CG_WIDTHS.put('s', 0.3333f);  CG_WIDTHS.put('t', 0.2667f);
        CG_WIDTHS.put('u', 0.3667f);  CG_WIDTHS.put('v', 0.3333f);
        CG_WIDTHS.put('w', 0.5333f);  CG_WIDTHS.put('x', 0.3667f);
        CG_WIDTHS.put('y', 0.3333f);  CG_WIDTHS.put('z', 0.4000f);
        // ── 數字 0–9 ──────────────────────────────
        CG_WIDTHS.put('0', 0.4333f);  CG_WIDTHS.put('1', 0.3333f);
        CG_WIDTHS.put('2', 0.4000f);  CG_WIDTHS.put('3', 0.3667f);
        CG_WIDTHS.put('4', 0.4000f);  CG_WIDTHS.put('5', 0.3667f);
        CG_WIDTHS.put('6', 0.4000f);  CG_WIDTHS.put('7', 0.3667f);
        CG_WIDTHS.put('8', 0.4000f);  CG_WIDTHS.put('9', 0.3600f);
        // ── 標點符號（量測值） ─────────────────────
        CG_WIDTHS.put('(', 0.2000f);  CG_WIDTHS.put(')', 0.2667f);
        CG_WIDTHS.put('-', 0.6000f);
        // ── 常用標點（估算值，可日後校準） ──────────
        CG_WIDTHS.put(' ', 0.2333f);  CG_WIDTHS.put('.', 0.2333f);
        CG_WIDTHS.put(',', 0.2333f);  CG_WIDTHS.put(':', 0.2333f);
        CG_WIDTHS.put(';', 0.2333f);  CG_WIDTHS.put('!', 0.2333f);
        CG_WIDTHS.put('?', 0.3000f);  CG_WIDTHS.put('/', 0.2000f);
        CG_WIDTHS.put('\\',0.2000f);  CG_WIDTHS.put('|', 0.2000f);
        CG_WIDTHS.put('+', 0.3333f);  CG_WIDTHS.put('=', 0.3333f);
        CG_WIDTHS.put('_', 0.3333f);  CG_WIDTHS.put('@', 0.5000f);
        CG_WIDTHS.put('#', 0.3667f);  CG_WIDTHS.put('$', 0.3000f);
        CG_WIDTHS.put('%', 0.4333f);  CG_WIDTHS.put('&', 0.4000f);
        CG_WIDTHS.put('*', 0.2667f);  CG_WIDTHS.put('<', 0.3333f);
        CG_WIDTHS.put('>', 0.3333f);  CG_WIDTHS.put('"', 0.2333f);
        CG_WIDTHS.put('\'',0.2333f);
    }

    private void checkBarcodeGap(List<RenderWarning> warnings) {
        if (minBarcodeGapDots <= 0) return;
        List<FieldRecord> barcodes = renderedFields.stream()
                .filter(f -> BARCODE_TYPES.contains(f.type()))
                .toList();

        for (int i = 0; i < barcodes.size(); i++) {
            for (int j = i + 1; j < barcodes.size(); j++) {
                FieldRecord a = barcodes.get(i);
                FieldRecord b = barcodes.get(j);

                // 只偵測水平相鄰（Y 範圍有交疊）的條碼對
                int yOverlap = Math.min(a.y() + a.h(), b.y() + b.h()) - Math.max(a.y(), b.y());
                if (yOverlap <= 0) continue;

                // 計算水平間距（兩框不重疊時才有意義；重疊已由 checkOverlaps 處理）
                int gap;
                if (a.x() + a.w() <= b.x()) {
                    gap = b.x() - (a.x() + a.w());
                } else if (b.x() + b.w() <= a.x()) {
                    gap = a.x() - (b.x() + b.w());
                } else {
                    continue; // 水平重疊，已被 OVERLAP 處理
                }

                if (gap < minBarcodeGapDots) {
                    RenderWarning w = new RenderWarning();
                    w.setType("BARCODE_GAP");
                    w.setFieldA(a.type() + ": " + a.label());
                    w.setFieldB(b.type() + ": " + b.label());
                    w.setDetail(String.format("水平間距 %.1f mm（最少需 %.1f mm）",
                            (double) gap / dpmm, (double) minBarcodeGapDots / dpmm));
                    w.setBoundsA(new int[]{a.x(), a.y(), a.w(), a.h()});
                    w.setBoundsB(new int[]{b.x(), b.y(), b.w(), b.h()});
                    warnings.add(w);
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Step 3（選用）— Debug Overlay
    // ═════════════════════════════════════════════════════════════════

    /**
     * 在已渲染的圖片上疊加 Debug 標註：
     * - 藍色實線：標籤邊界
     * - 紅色虛線框 + 半透明紅填色：超出邊界的欄位（取可見部分）
     * - 橘色虛線框 + 半透明橘填色：重疊欄位對 + 交集區
     * - 藍色虛線框 + 半透明藍填色：條碼間距不足的欄位對及其間距區
     */
    public void applyDebugOverlay(List<RenderWarning> warnings) {
        Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                        10, new float[]{6, 4}, 0);
        Stroke solid  = new BasicStroke(2);

        for (RenderWarning w : warnings) {
            if ("OUT_OF_BOUNDS".equals(w.getType())) {
                int[] b = w.getBoundsA();
                // 取在 label 範圍內的可見矩形
                int vx = clamp(b[0], 0, labelWidthDots);
                int vy = clamp(b[1], 0, labelHeightDots);
                int vw = clamp(b[0] + b[2], 0, labelWidthDots) - vx;
                int vh = clamp(b[1] + b[3], 0, labelHeightDots) - vy;
                if (vw <= 0 || vh <= 0) continue;

                // 半透明紅色填色
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
                g.setColor(new Color(220, 30, 30));
                g.fillRect(vx, vy, vw, vh);

                // 紅色虛線框
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(new Color(180, 0, 0));
                g.setStroke(dashed);
                g.drawRect(vx, vy, vw, vh);

            } else if ("OVERLAP".equals(w.getType())) {
                int[] inter = w.getIntersect();
                int[] bA    = w.getBoundsA();
                int[] bB    = w.getBoundsB();

                // 半透明橘色填色在交集區
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                g.setColor(new Color(255, 140, 0));
                g.fillRect(inter[0], inter[1], inter[2], inter[3]);

                // 橘色虛線框框住兩個欄位
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(new Color(200, 100, 0));
                g.setStroke(dashed);
                g.drawRect(bA[0], bA[1], bA[2], bA[3]);
                g.drawRect(bB[0], bB[1], bB[2], bB[3]);

            } else if ("BARCODE_GAP".equals(w.getType())) {
                int[] bA = w.getBoundsA();
                int[] bB = w.getBoundsB();

                // 計算兩條碼之間的水平 gap 矩形
                int gx    = Math.min(bA[0] + bA[2], bB[0] + bB[2]); // 左條碼的右緣
                int gxEnd = Math.max(bA[0], bB[0]);                  // 右條碼的左緣
                int gy    = Math.max(bA[1], bB[1]);                   // Y 交疊上緣
                int gyEnd = Math.min(bA[1] + bA[3], bB[1] + bB[3]); // Y 交疊下緣
                int gw    = gxEnd - gx;
                int gh    = gyEnd - gy;

                // 半透明紫色填色在 gap 區域
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                g.setColor(new Color(0, 0, 220));
                if (gw > 0 && gh > 0) g.fillRect(gx, gy, gw, gh);

                // 紫色虛線框框住兩個條碼
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(new Color(0, 0, 220));
                g.setStroke(dashed);
                g.drawRect(bA[0], bA[1], bA[2], bA[3]);
                g.drawRect(bB[0], bB[1], bB[2], bB[3]);
            }
        }

        // 標籤邊界（藍色實線，永遠繪製）
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(30, 100, 220));
        g.setStroke(solid);
        g.drawRect(1, 1, labelWidthDots - 2, labelHeightDots - 2);
        g.setStroke(new BasicStroke(1));
    }

    // ═════════════════════════════════════════════════════════════════
    //  Step 4 — 輸出 PNG
    // ═════════════════════════════════════════════════════════════════

    public byte[] toPng() throws Exception {
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────
    //  ZPL Parser
    // ─────────────────────────────────────────────────────────────────

    private void parse(String zpl) {
        currentLine = 1;
        syntaxWarnings.clear();
        warnedCmds.clear();
        int i = 0;
        while (i < zpl.length()) {
            char c = zpl.charAt(i);
            if (c == '\n') { currentLine++; i++; continue; }
            if (c == '~') {
                i++;
                if (i + 2 <= zpl.length()) {
                    String tcmd = zpl.substring(i, Math.min(i + 2, zpl.length())).toUpperCase();
                    if ("DG".equals(tcmd)) {
                        i = parseTildeDG(zpl, i + 2);
                    } else {
                        while (i < zpl.length() && zpl.charAt(i) != '^' && zpl.charAt(i) != '~') i++;
                    }
                }
                continue;
            }
            if (c != '^') { i++; continue; }

            i++;
            if (i >= zpl.length()) break;

            int    cmdEnd = Math.min(i + 2, zpl.length());
            String cmd    = zpl.substring(i, cmdEnd).toUpperCase();
            i = cmdEnd;

            if ("FD".equals(cmd)) {
                int    fsIdx = findCaret(zpl, i, "FS");
                String data  = (fsIdx < 0) ? zpl.substring(i) : zpl.substring(i, fsIdx);
                i = (fsIdx < 0) ? zpl.length() : fsIdx + 3;   // skip ^FS
                onFieldData(data);
            } else {
                int end = i;
                while (end < zpl.length() && zpl.charAt(end) != '^' && zpl.charAt(end) != '~') end++;
                String params = zpl.substring(i, end).trim().replaceAll("[\r\n\t]", "");
                i = end;
                onCommand(cmd, params);
            }
        }
    }

    /** 從 from 開始尋找 ^CMD，回傳 ^ 的 index，找不到回傳 -1。 */
    private int findCaret(String zpl, int from, String cmd) {
        for (int i = from; i + 2 < zpl.length(); i++) {
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

            case "FO" -> { fieldOriginX = intAt(p, 0, 0);          fieldOriginY = intAt(p, 1, 0); }
            case "FT" -> { fieldOriginX = intAt(p, 0, fieldOriginX); fieldOriginY = intAt(p, 1, fieldOriginY); }

            // ^A0 = CG Triumvirate（scalable, bold proportional）
            case "A0" -> {
                fontFace = '0';
                if (p.length > 0 && !p[0].isEmpty()) {
                    if (!isValidOrientation(p[0]))
                        addSyntaxWarn("A0", "方向參數 '" + p[0] + "' 無效，必須為 N/R/I/B");
                    fontOrientation = p[0].toUpperCase().charAt(0);
                }
                fontHeight = intAt(p, 1, fontHeight);
                fontWidth  = intAt(p, 2, 0);
                if (fontHeight < 1) addSyntaxWarn("A0", "字型高度 h=" + fontHeight + " 無效，必須 ≥ 1");
            }
            // ^AA – ^AZ = bitmap fonts A-Z（monospaced style）
            case "AA","AB","AC","AD","AE","AF","AG","AH","AI","AJ","AK","AL","AM",
                 "AN","AO","AP","AQ","AR","AS","AT","AU","AV","AW","AX","AY","AZ",
                 "A1","A2","A3","A4","A5","A6","A7","A8","A9" -> {
                fontFace = cmd.charAt(1);
                if (p.length > 0 && !p[0].isEmpty()) fontOrientation = p[0].toUpperCase().charAt(0);
                fontHeight = intAt(p, 1, fontHeight);
                fontWidth  = intAt(p, 2, 0);
                if (fontHeight < 1) addSyntaxWarn(cmd, "字型高度 h=" + fontHeight + " 無效，必須 ≥ 1");
            }
            // ^CFf,h = change default font (f = font id, h = height)
            case "CF" -> {
                if (p.length > 0 && !p[0].isEmpty()) fontFace = p[0].toUpperCase().charAt(0);
                fontHeight = intAt(p, 1, fontHeight);
                if (fontHeight < 1) addSyntaxWarn("CF", "字型高度 h=" + fontHeight + " 無效，必須 ≥ 1");
            }
            // ^FR = Field Reverse（次個欄位前景/背景互換）
            case "FR" -> fieldReverse = true;

            case "BY" -> {
                barcodeModuleWidth = intAt(p, 0, barcodeModuleWidth);
                barcodeHeight      = intAt(p, 2, barcodeHeight);
                if (barcodeModuleWidth < 1)
                    addSyntaxWarn("BY", "模組寬度 mw=" + barcodeModuleWidth + " 無效，必須 ≥ 1");
                if (barcodeHeight < 1)
                    addSyntaxWarn("BY", "條碼高度 h=" + barcodeHeight + " 無效，必須 ≥ 1");
            }

            case "BC" -> {
                if (p.length > 0 && !p[0].isEmpty() && !isValidOrientation(p[0]))
                    addSyntaxWarn("BC", "方向參數 '" + p[0] + "' 無效，必須為 N/R/I/B");
                pendingBarcodeType = "BC"; pendingBarcodeParams = p;
            }
            case "B3" -> {
                if (p.length > 0 && !p[0].isEmpty() && !isValidOrientation(p[0]))
                    addSyntaxWarn("B3", "方向參數 '" + p[0] + "' 無效，必須為 N/R/I/B");
                pendingBarcodeType = "B3"; pendingBarcodeParams = p;
            }
            case "BQ" -> { pendingBarcodeType = "BQ"; pendingBarcodeParams = p; }
            case "BX" -> { pendingBarcodeType = "BX"; pendingBarcodeParams = p; }
            case "BE" -> { pendingBarcodeType = "BE"; pendingBarcodeParams = p; }
            case "BU" -> { pendingBarcodeType = "BU"; pendingBarcodeParams = p; }

            case "GB" -> {
                int w     = intAt(p, 0, 1);
                int h     = intAt(p, 1, 1);
                int thick = intAt(p, 2, 1);
                if (w < 1)     addSyntaxWarn("GB", "寬度 w=" + w + " 無效，必須 ≥ 1");
                if (h < 1)     addSyntaxWarn("GB", "高度 h=" + h + " 無效，必須 ≥ 1");
                if (thick < 1) addSyntaxWarn("GB", "線條厚度 t=" + thick + " 無效，必須 ≥ 1");
                Color col = (p.length > 3 && "W".equalsIgnoreCase(p[3].trim())) ? Color.WHITE : Color.BLACK;
                if (fieldReverse) {
                    g.setColor(Color.BLACK);
                    g.setXORMode(Color.WHITE);
                    drawBox(w, h, thick, Color.BLACK, intAt(p, 4, 0));
                    g.setPaintMode();
                } else {
                    drawBox(w, h, thick, col, intAt(p, 4, 0));
                }
                fieldReverse = false;
            }

            case "FS" -> { pendingBarcodeType = null; pendingBarcodeParams = null; fieldReverse = false; }

            case "XG" -> {
                // params: "R:filename.GRF,magX,magY"
                String filename = p[0].trim();
                BufferedImage stored = graphicStore.get(filename);
                if (stored != null) {
                    g.drawImage(stored, fieldOriginX, fieldOriginY, null);
                    renderedFields.add(new FieldRecord(fieldOriginX, fieldOriginY,
                            stored.getWidth(), stored.getHeight(), "GRAPHIC", filename));
                }
            }

            default -> {
                if (!KNOWN_SILENT_CMDS.contains(cmd)) {
                    String unsupportedMsg = UNSUPPORTED_CMD_MSGS.get(cmd);
                    if (unsupportedMsg != null) {
                        addUnknownCmdWarn(cmd, "不支援指令：" + unsupportedMsg);
                    } else {
                        addUnknownCmdWarn(cmd, "未知指令 ^" + cmd + "，將被略過");
                    }
                }
            }
        }
    }

    private static boolean isValidOrientation(String s) {
        if (s == null || s.isEmpty()) return true;
        return "N".equalsIgnoreCase(s) || "R".equalsIgnoreCase(s)
            || "I".equalsIgnoreCase(s) || "B".equalsIgnoreCase(s);
    }

    private void resetState() {
        fieldOriginX = 0; fieldOriginY = 0;
        fontFace = '0';  fontHeight = 30;  fontWidth = 0;  fontOrientation = 'N';
        fieldReverse = false;
        barcodeModuleWidth = 2; barcodeHeight = defaultBarcodeHeight;
        pendingBarcodeType = null; pendingBarcodeParams = null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Field data → 渲染 + 記錄 BoundingBox
    // ─────────────────────────────────────────────────────────────────

    private void onFieldData(String data) {
        try {
            if (pendingBarcodeType != null) renderBarcode(data);
            else                            renderText(data);
        } catch (Exception ex) {
            renderText("[Err: " + ex.getMessage() + "]");
        }
        pendingBarcodeType   = null;
        pendingBarcodeParams = null;
        fieldReverse         = false;
    }

    // ── Text ─────────────────────────────────────────────────────────

    private void renderText(String text) {
        int fh = Math.max(fontHeight, 8);

        // Font 0 → 嵌入的 BarlowCondensed-Bold；Font A-Z → 內建等寬字型
        Font font = (fontFace == '0')
                ? cgFont.deriveFont(Font.PLAIN, (float) fh)
                : new Font(Font.MONOSPACED, Font.PLAIN, fh);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        // 大寫 'H' 的視覺高度 → baseline 計算基準
        int capHpx = (int) Math.round(
                font.createGlyphVector(g.getFontRenderContext(), "H")
                    .getVisualBounds().getHeight());

        // effectiveW：用於查表縮放
        //   指定 fontWidth  → 使用 fontWidth
        //   未指定          → 使用 fontHeight（對應 CG Triumvirate 的自然比例）
        int effectiveW = (fontWidth > 0) ? fontWidth : fontHeight;

        // 計算字串總寬（查 CG_WIDTHS 表）
        int textW = 0;
        for (int ci = 0; ci < text.length(); ci++)
            textW += cgCharWidth(text.charAt(ci), effectiveW, fm);
        int textH = (int) Math.round(fh * 0.8);

        // BoundingBox（旋轉時 w/h 互換）
        int recW = (fontOrientation == 'R' || fontOrientation == 'B') ? textH : textW;
        int recH = (fontOrientation == 'R' || fontOrientation == 'B') ? textW : textH;
        renderedFields.add(new FieldRecord(fieldOriginX, fieldOriginY, recW, recH,
                "TEXT", truncate(text, 25)));

        g.setColor(fieldReverse ? Color.WHITE : Color.BLACK);

        AffineTransform saved = g.getTransform();
        int baselineY;
        if (fontOrientation == 'R') {
            g.translate(fieldOriginX + fh,    fieldOriginY);         g.rotate( Math.PI / 2); baselineY = capHpx;
        } else if (fontOrientation == 'I') {
            g.translate(fieldOriginX + textW, fieldOriginY + fh);    g.rotate( Math.PI);     baselineY = capHpx;
        } else if (fontOrientation == 'B') {
            g.translate(fieldOriginX,         fieldOriginY + textW); g.rotate(-Math.PI / 2); baselineY = capHpx;
        } else {
            g.translate(fieldOriginX, 0);                                                     baselineY = fieldOriginY + capHpx;
        }
        drawCharsWithCgWidths(text, 0, baselineY, fm, effectiveW);
        g.setTransform(saved);
        fieldReverse = false;
    }

    /**
     * 逐字繪製文字：依 CG_WIDTHS 查表計算每字元目標寬度，
     * 再以 per-character AffineTransform 精確縮放到目標寬度後繪製。
     */
    private void drawCharsWithCgWidths(String text, float startX, int baselineY,
                                        FontMetrics fm, int effectiveW) {
        AffineTransform base = g.getTransform();
        float penX = startX;
        for (int ci = 0; ci < text.length(); ci++) {
            char c = text.charAt(ci);
            int targetW  = cgCharWidth(c, effectiveW, fm);
            int naturalW = Math.max(1, fm.charWidth(c));
            double charXScale = (double) targetW / naturalW;

            g.setTransform(base);
            g.translate(penX, 0);
            if (Math.abs(charXScale - 1.0) > 0.005) g.scale(charXScale, 1.0);
            g.drawString(String.valueOf(c), 0, baselineY);
            penX += targetW;
        }
        g.setTransform(base);
    }

    /**
     * 查 CG_WIDTHS 表，回傳字元 c 的目標像素寬度。
     * Font 0 → 查表；其他字型 → 使用字型本身的 advance width。
     */
    private int cgCharWidth(char c, int effectiveW, FontMetrics fm) {
        if (fontFace == '0') {
            float ratio = CG_WIDTHS.getOrDefault(c, CG_DEFAULT_RATIO);
            return Math.max(1, Math.round(ratio * effectiveW / CG_REF_RATIO));
        }
        return fm.charWidth(c);
    }

    // ── Barcode routing ───────────────────────────────────────────────

    private void renderBarcode(String data) throws Exception {
        switch (pendingBarcodeType) {
            case "BC"      -> renderCode128(data);
            case "B3"      -> renderCode39(data);
            case "BQ"      -> renderQRCode(data);
            case "BX"      -> renderDataMatrix(data);
            case "BE","BU" -> renderCode128(data);  // fallback
            default        -> renderCode128(data);
        }
    }

    private void renderCode128(String data) throws Exception {
        int bh        = intAt(pendingBarcodeParams, 1, barcodeHeight);
        boolean print = !"N".equalsIgnoreCase(safeGet(pendingBarcodeParams, 2, "Y"));
        int hriSize   = Math.max(18, barcodeModuleWidth * 10);
        int textExtra = print ? (hriSize + 4) : 0;

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        // ZPL ^BC default mode = Code128-B (Zebra/Labelary never auto-switch to C).
        hints.put(EncodeHintType.FORCE_CODE_SET, "B");

        // Two-step: encode at width=1 → ZXing scale=1 → matrix.getWidth() = exact module count.
        // Then re-encode at moduleCount × barcodeModuleWidth for pixel-perfect bar widths.
        int moduleCount = new Code128Writer().encode(data, BarcodeFormat.CODE_128, 1, 1, hints).getWidth();
        int barcodeW    = moduleCount * barcodeModuleWidth;
        BitMatrix matrix  = new Code128Writer().encode(data, BarcodeFormat.CODE_128, barcodeW, bh, hints);
        BufferedImage bar = MatrixToImageWriter.toBufferedImage(matrix);

        g.drawImage(bar, fieldOriginX, fieldOriginY, null);

        if (print) {
            // HRI font: SANS_SERIF (proportional), matching real Zebra printer output
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, hriSize));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.BLACK);
            int tx = fieldOriginX + (bar.getWidth() - fm.stringWidth(data)) / 2;
            g.drawString(data, Math.max(tx, fieldOriginX), fieldOriginY + bh + fm.getAscent() + 2);
        }

        renderedFields.add(new FieldRecord(
                fieldOriginX, fieldOriginY, bar.getWidth(), bh + textExtra, "CODE128", truncate(data, 20)));
    }

    private void renderCode39(String data) throws Exception {
        data = data.toUpperCase();  // Code 39 不支援小寫，自動轉換
        int bh = intAt(pendingBarcodeParams, 1, barcodeHeight);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);

        // Two-step for exact module width
        int moduleCount = new Code39Writer().encode(data, BarcodeFormat.CODE_39, 1, 1, hints).getWidth();
        int barcodeW    = moduleCount * barcodeModuleWidth;
        BitMatrix matrix  = new Code39Writer().encode(data, BarcodeFormat.CODE_39, barcodeW, bh, hints);
        BufferedImage bar = MatrixToImageWriter.toBufferedImage(matrix);

        g.drawImage(bar, fieldOriginX, fieldOriginY, null);
        renderedFields.add(new FieldRecord(
                fieldOriginX, fieldOriginY, bar.getWidth(), bh, "CODE39", truncate(data, 20)));
    }

    private void renderQRCode(String data) throws Exception {
        int magnification = intAt(pendingBarcodeParams, 2, 3);
        int size = Math.max(magnification * 20, 80);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints);
        BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);

        g.drawImage(qr, fieldOriginX, fieldOriginY, null);
        int[] cb = matrixContentBounds(matrix);
        renderedFields.add(new FieldRecord(
                fieldOriginX + cb[0], fieldOriginY + cb[1], cb[2], cb[3], "QRCODE", truncate(data, 20)));
    }

    private void renderDataMatrix(String data) throws Exception {
        // ^BXN,{h},{q}  — h = element height (dots), q = quality (ignored here)
        int bh = intAt(pendingBarcodeParams, 1, barcodeHeight);
        int size = Math.max(bh, 10);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        BitMatrix matrix = new DataMatrixWriter().encode(data, BarcodeFormat.DATA_MATRIX, size, size, hints);
        BufferedImage dm = MatrixToImageWriter.toBufferedImage(matrix);

        g.drawImage(dm, fieldOriginX, fieldOriginY, null);
        int[] cb = matrixContentBounds(matrix);
        renderedFields.add(new FieldRecord(
                fieldOriginX + cb[0], fieldOriginY + cb[1], cb[2], cb[3], "DATAMATRIX", truncate(data, 20)));
    }

    /** 掃描 BitMatrix，回傳 [x, y, w, h]，即包含所有 set bit 的最小矩形（pixel 單位）。
     *  若 matrix 全空，則回傳整張圖大小。 */
    private static int[] matrixContentBounds(BitMatrix matrix) {
        int minX = matrix.getWidth(), maxX = -1, minY = matrix.getHeight(), maxY = -1;
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX == -1) return new int[]{0, 0, matrix.getWidth(), matrix.getHeight()};
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    // ── Graphic Box ───────────────────────────────────────────────────

    private void drawBox(int width, int height, int thickness, Color color, int rounding) {
        g.setColor(color);
        Stroke saved  = g.getStroke();
        boolean filled = (thickness * 2 >= Math.min(width, height));

        if (rounding > 0) {
            int arc = rounding * Math.min(width, height) / 8;
            if (filled) g.fillRoundRect(fieldOriginX, fieldOriginY, width, height, arc, arc);
            else { g.setStroke(new BasicStroke(thickness)); g.drawRoundRect(fieldOriginX, fieldOriginY, width, height, arc, arc); }
        } else {
            if (filled) g.fillRect(fieldOriginX, fieldOriginY, width, height);
            else { g.setStroke(new BasicStroke(thickness)); g.drawRect(fieldOriginX, fieldOriginY, width, height); }
        }

        g.setStroke(saved);
        g.setColor(Color.BLACK);

        renderedFields.add(new FieldRecord(
                fieldOriginX, fieldOriginY, width, height, "BOX", width + "×" + height));
    }

    // ─────────────────────────────────────────────────────────────────
    //  ~DG parser: ~DGR:filename,totalBytes,bytesPerRow,hexData
    // ─────────────────────────────────────────────────────────────────

    /**
     * Parse a ~DG command starting right after "DG" (i.e., at the first char of params).
     * Returns the position after all consumed characters.
     */
    private int parseTildeDG(String zpl, int start) {
        // Read until next ^ or ~ or end
        int end = start;
        // We need to read: R:filename,totalBytes,bytesPerRow,hexData
        // hexData can be very long and may include newlines between rows
        // Strategy: read comma-separated first 3 fields, then collect hex chars

        int len = zpl.length();

        // Skip to first comma after "R:filename"
        int c1 = zpl.indexOf(',', start);
        if (c1 < 0) return len;
        String filename = zpl.substring(start, c1).trim();

        int c2 = zpl.indexOf(',', c1 + 1);
        if (c2 < 0) return len;
        int totalBytes;
        int bytesPerRow;
        try {
            totalBytes = Integer.parseInt(zpl.substring(c1 + 1, c2).trim());
        } catch (NumberFormatException e) { return c2; }

        int c3 = zpl.indexOf(',', c2 + 1);
        if (c3 < 0) return len;
        try {
            bytesPerRow = Integer.parseInt(zpl.substring(c2 + 1, c3).trim());
        } catch (NumberFormatException e) { return c3; }

        // Collect exactly totalBytes*2 hex chars (skip whitespace/newlines)
        int hexPos = c3 + 1;
        int hexCharsNeeded = totalBytes * 2;
        StringBuilder hexSb = new StringBuilder(hexCharsNeeded);
        while (hexPos < len && hexSb.length() < hexCharsNeeded) {
            char ch = zpl.charAt(hexPos);
            if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F') || (ch >= 'a' && ch <= 'f')) {
                hexSb.append(ch);
            }
            hexPos++;
        }

        if (bytesPerRow <= 0 || totalBytes <= 0) return hexPos;

        int height = totalBytes / bytesPerRow;
        int width  = bytesPerRow * 8;

        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            String hexData = hexSb.toString().toUpperCase();

            for (int row = 0; row < height; row++) {
                for (int bx = 0; bx < bytesPerRow; bx++) {
                    int charIdx = (row * bytesPerRow + bx) * 2;
                    if (charIdx + 2 > hexData.length()) break;
                    int byteVal = Integer.parseInt(hexData.substring(charIdx, charIdx + 2), 16);
                    for (int bit = 0; bit < 8; bit++) {
                        int px = bx * 8 + bit;
                        if (px < width) {
                            boolean isBlack = (byteVal & (0x80 >> bit)) != 0;
                            img.setRGB(px, row, isBlack ? java.awt.Color.BLACK.getRGB()
                                                        : java.awt.Color.WHITE.getRGB());
                        }
                    }
                }
            }
            graphicStore.put(filename, img);
        } catch (Exception ignored) {
            // Malformed hex data — skip
        }

        return hexPos;
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
        if (arr == null || idx >= arr.length) return def;
        String v = arr[idx].trim();
        return v.isEmpty() ? def : v;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "…" : s;
    }
}
