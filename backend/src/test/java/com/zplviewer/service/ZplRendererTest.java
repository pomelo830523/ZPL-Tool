package com.zplviewer.service;

import com.zplviewer.model.RenderWarning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ZplRenderer covering:
 *  - render/toPng lifecycle
 *  - text rendering (orientations, ^CF, ^FR, ^BY)
 *  - barcode rendering (Code128, Code39, QR, DataMatrix)
 *  - graphic box (^GB)
 *  - ~DG / ^XG graphic store
 *  - analyze: OUT_OF_BOUNDS, OVERLAP, BARCODE_GAP
 *  - applyDebugOverlay: all three warning types
 */
class ZplRendererTest {

    // 2000×1500 dots @ dpmm=8 — large enough to avoid accidental OUT_OF_BOUNDS
    private static final int W    = 2000;
    private static final int H    = 1500;
    private static final int DPMM = 8;
    private static final int MIN_GAP_DOTS = 0; // disabled by default; enabled in gap tests

    private ZplRenderer renderer(int minGapDots) {
        return new ZplRenderer(W, H, DPMM, 100, minGapDots);
    }

    private ZplRenderer renderer() {
        return renderer(MIN_GAP_DOTS);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    @Test
    void render_toPng_producesNonEmptyBytes() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO100,100^A0N,30^FDHello^FS^XZ");
        byte[] png = r.toPng();
        assertNotNull(png);
        assertTrue(png.length > 0);
        // PNG header magic: 0x89 PNG
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]); // 'P'
        assertEquals((byte) 0x4E, png[2]); // 'N'
    }

    @Test
    void render_smallDimensions_clampedTo50() {
        // Constructor must clamp width/height to at least 50
        ZplRenderer r = new ZplRenderer(10, 10, DPMM, 100, 0);
        assertDoesNotThrow(() -> {
            r.render("^XA^XZ");
            r.toPng();
        });
    }

    @Test
    void render_emptyZpl_producesWhiteImage() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^XZ");
        byte[] png = r.toPng();
        assertTrue(png.length > 0);
    }

    // ── Text rendering ────────────────────────────────────────────────

    @Test
    void render_textWithHyphen() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^A0N,40^FDHello-World^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_textOrientationR() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^A0R,40^FDRotated^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_textOrientationI() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^A0I,40^FDInverted^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_textOrientationB() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^A0B,40^FDBottom^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_fontA_monospaced() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^AAN,30^FDMonospaced^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_cfChangeFont() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^CF0,50^FO200,200^FDChanged Font^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_fieldReverse() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^FR^A0N,30^FDReversed^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_ftFieldTranslate() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FT300,300^A0N,30^FDTranslated^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_explicitFontWidth() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^A0N,40,60^FDWide^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_xaResetsState() throws Exception {
        ZplRenderer r = renderer();
        // First label sets state, second ^XA should reset it
        r.render("^XA^BY3^FO500,500^A0R,50^FDFirst^FS^XA^FO200,200^A0N,30^FDSecond^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    // ── Barcode rendering ─────────────────────────────────────────────

    @Test
    void render_code128_withHRI() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^BY2,,100^BCN,100,Y^FD12345678^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_code128_noHRI() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^BY2,,100^BCN,100,N^FD12345678^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_code39() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^BY2,,100^B3N,N,100^FDHELLO^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_qrcode() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^BQN,2,5^FDhttps://example.com^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_datamatrix() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^BXN,5,200^FDABCDEF^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_barcodeBy_updatesDefaults() throws Exception {
        ZplRenderer r = renderer();
        // ^BY sets moduleWidth=3 and height=150
        r.render("^XA^FO200,200^BY3,,150^BCN^FD123^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    // ── Graphic Box ───────────────────────────────────────────────────

    @Test
    void render_gbFilledBox() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^GB200,200,200^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_gbOutlineBox() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^GB300,100,3^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_gbWhiteBox() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^GB200,200,200,W^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_gbRoundedBox() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^GB200,200,5,,4^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_gbWithFieldReverse() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^FR^GB100,100,5^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    // ── ~DG / ^XG graphic store ───────────────────────────────────────

    @Test
    void render_dgStore_xgRecall() throws Exception {
        // ~DG: 4×4 pixel image = 4 bytes (4 rows × 1 byte), bytesPerRow=1
        // Each byte: 0xFF = 8 black pixels
        String dgHex = "FFFFFFFF"; // 4 rows, 8 dots wide each, all black
        String zpl = "~DGR:LOGO.GRF,4,1," + dgHex
                + "^XA^FO200,200^XGR:LOGO.GRF,1,1^FS^XZ";
        ZplRenderer r = renderer();
        r.render(zpl);
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_dgMalformedHex_doesNotThrow() throws Exception {
        ZplRenderer r = renderer();
        r.render("~DGR:BAD.GRF,4,1,ZZZZ^XA^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_xg_unknownFile_ignored() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO100,100^XGR:NOTFOUND.GRF,1,1^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    // ── analyze: OUT_OF_BOUNDS ────────────────────────────────────────

    @Test
    void analyze_outOfBounds_leftTop() throws Exception {
        ZplRenderer r = renderer();
        // Place text at (0,0) — inside margin (3mm*8=24 dots)
        r.render("^XA^FO0,0^A0N,30^FDEdge^FS^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertTrue(warnings.stream().anyMatch(w -> "OUT_OF_BOUNDS".equals(w.getType())));
    }

    @Test
    void analyze_outOfBounds_rightBottom() throws Exception {
        ZplRenderer r = renderer();
        // Place a wide box that extends beyond the safe area
        r.render("^XA^FO" + (W - 10) + "," + (H - 10) + "^GB200,200,200^FS^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertTrue(warnings.stream().anyMatch(w -> "OUT_OF_BOUNDS".equals(w.getType())));
    }

    @Test
    void analyze_noOutOfBounds_forCenteredField() throws Exception {
        ZplRenderer r = renderer();
        // Well-centered field, should NOT trigger OUT_OF_BOUNDS
        r.render("^XA^FO500,500^A0N,30^FDCenter^FS^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertFalse(warnings.stream().anyMatch(w -> "OUT_OF_BOUNDS".equals(w.getType())));
    }

    // ── analyze: OVERLAP ──────────────────────────────────────────────

    @Test
    void analyze_overlap_detected() throws Exception {
        ZplRenderer r = renderer();
        // Two large boxes at the same position → definitely overlapping
        r.render("^XA"
                + "^FO400,400^GB400,200,200^FS"
                + "^FO400,400^GB400,200,200^FS"
                + "^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertTrue(warnings.stream().anyMatch(w -> "OVERLAP".equals(w.getType())));
    }

    @Test
    void analyze_overlap_belowThreshold_notReported() throws Exception {
        ZplRenderer r = renderer();
        // Two boxes touching by 1 dot — with threshold=5mm*8=40 dots, should NOT trigger
        r.render("^XA"
                + "^FO400,400^GB100,100,100^FS"
                + "^FO499,499^GB100,100,100^FS"
                + "^XZ");
        // Overlap is 1×1 dot; threshold 5mm = 40 dots → not detected
        List<RenderWarning> warnings = r.analyze(5.0);
        assertFalse(warnings.stream().anyMatch(w -> "OVERLAP".equals(w.getType())));
    }

    @Test
    void analyze_noOverlap_forSeparatedFields() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA"
                + "^FO200,200^A0N,30^FDFirst^FS"
                + "^FO800,200^A0N,30^FDSecond^FS"
                + "^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertFalse(warnings.stream().anyMatch(w -> "OVERLAP".equals(w.getType())));
    }

    // ── analyze: BARCODE_GAP ──────────────────────────────────────────

    @Test
    void analyze_barcodeGap_disabled_noWarning() throws Exception {
        // minGapDots=0 disables the check entirely
        ZplRenderer r = renderer(0);
        r.render("^XA"
                + "^FO200,200^BCN,100,N^FDA^FS"
                + "^FO210,200^BCN,100,N^FDB^FS"
                + "^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertFalse(warnings.stream().anyMatch(w -> "BARCODE_GAP".equals(w.getType())));
    }

    @Test
    void analyze_barcodeGap_detected() throws Exception {
        // minGapDots=1000 (very large) to force detection
        ZplRenderer r = renderer(1000);
        r.render("^XA"
                + "^FO200,200^BY2,,100^BCN,100,N^FDA12345^FS"
                + "^FO600,200^BY2,,100^BCN,100,N^FDB12345^FS"
                + "^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertTrue(warnings.stream().anyMatch(w -> "BARCODE_GAP".equals(w.getType())));
    }

    @Test
    void analyze_barcodeGap_verticalNeighbors_notFlagged() throws Exception {
        // Two barcodes stacked vertically (no Y overlap) → gap check skipped
        ZplRenderer r = renderer(1000);
        r.render("^XA"
                + "^FO200,100^BY2,,80^BCN,80,N^FDTOP^FS"
                + "^FO200,800^BY2,,80^BCN,80,N^FDBOTTOM^FS"
                + "^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertFalse(warnings.stream().anyMatch(w -> "BARCODE_GAP".equals(w.getType())));
    }

    // ── applyDebugOverlay ─────────────────────────────────────────────

    @Test
    void applyDebugOverlay_outOfBounds() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO0,0^A0N,30^FDEdge^FS^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertDoesNotThrow(() -> r.applyDebugOverlay(warnings));
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void applyDebugOverlay_overlap() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA"
                + "^FO400,400^GB300,200,200^FS"
                + "^FO400,400^GB300,200,200^FS"
                + "^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertDoesNotThrow(() -> r.applyDebugOverlay(warnings));
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void applyDebugOverlay_barcodeGap() throws Exception {
        ZplRenderer r = renderer(1000);
        r.render("^XA"
                + "^FO200,200^BY2,,100^BCN,100,N^FDA12345^FS"
                + "^FO600,200^BY2,,100^BCN,100,N^FDB12345^FS"
                + "^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertTrue(warnings.stream().anyMatch(w -> "BARCODE_GAP".equals(w.getType())));
        assertDoesNotThrow(() -> r.applyDebugOverlay(warnings));
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void applyDebugOverlay_barcodeGap_zeroWidthGap() throws Exception {
        // Manually construct a BARCODE_GAP warning where gw=0 to test the if-guard
        ZplRenderer r = renderer();
        r.render("^XA^FO500,500^A0N,30^FDSomething^FS^XZ");
        r.analyze(0); // must call analyze first (to build state)

        RenderWarning w = new RenderWarning();
        w.setType("BARCODE_GAP");
        w.setFieldA("CODE128: A");
        w.setFieldB("CODE128: B");
        // boundsA right-edge == boundsB left-edge → gw = 0
        w.setBoundsA(new int[]{100, 200, 150, 200}); // right at x=250
        w.setBoundsB(new int[]{250, 200, 150, 200}); // left  at x=250  → gap=0

        assertDoesNotThrow(() -> r.applyDebugOverlay(List.of(w)));
    }

    @Test
    void applyDebugOverlay_outOfBounds_zeroVisibleArea_skipped() throws Exception {
        // Manually build an OUT_OF_BOUNDS warning where the visible rect is empty
        ZplRenderer r = renderer();
        r.render("^XA^FO500,500^A0N,30^FDTest^FS^XZ");
        r.analyze(0);

        RenderWarning w = new RenderWarning();
        w.setType("OUT_OF_BOUNDS");
        w.setFieldA("TEXT: offscreen");
        // entirely outside label (x negative, w=0 after clamp)
        w.setBoundsA(new int[]{-500, -500, 10, 10});

        assertDoesNotThrow(() -> r.applyDebugOverlay(List.of(w)));
    }

    @Test
    void applyDebugOverlay_emptyWarnings_drawsBorder() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO500,500^A0N,30^FDTest^FS^XZ");
        assertDoesNotThrow(() -> r.applyDebugOverlay(List.of()));
        assertDoesNotThrow(() -> r.toPng());
    }

    // ── Fallback barcode types ─────────────────────────────────────────

    @Test
    void render_beFallbackToCode128() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^BY2,,100^BEN,100,N^FDBE-FALLBACK^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_buFallbackToCode128() throws Exception {
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^BY2,,100^BUN,100,N^FDBU-FALLBACK^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    // ── parseTildeDG edge cases ────────────────────────────────────────

    @Test
    void render_dgMissingFirstComma_doesNotThrow() throws Exception {
        ZplRenderer r = renderer();
        r.render("~DGNOCOMMAATALL^XA^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_dgMissingSecondComma_doesNotThrow() throws Exception {
        ZplRenderer r = renderer();
        r.render("~DGR:FILE.GRF,99^XA^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_dgInvalidTotalBytes_doesNotThrow() throws Exception {
        ZplRenderer r = renderer();
        r.render("~DGR:FILE.GRF,NOTANUMBER,1,FF^XA^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_dgInvalidBytesPerRow_doesNotThrow() throws Exception {
        ZplRenderer r = renderer();
        r.render("~DGR:FILE.GRF,4,NOTANUMBER,FFFFFFFF^XA^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void render_multipleLabels_xaResetsBarcode() throws Exception {
        // Verifies resetState() clears pendingBarcodeType
        ZplRenderer r = renderer();
        r.render("^XA^FO200,200^BCN,100,N^XA^FO200,200^A0N,30^FDAfterReset^FS^XZ");
        assertDoesNotThrow(() -> r.toPng());
    }

    @Test
    void analyze_outOfBounds_sideDetail() throws Exception {
        // Trigger multiple sides in one field to cover String.join("+", sides)
        ZplRenderer r = renderer();
        // Place at (0,0) with a box large enough to hit RIGHT+BOTTOM too
        r.render("^XA^FO0,0^GB" + W + "," + H + ",2^FS^XZ");
        List<RenderWarning> warnings = r.analyze(0);
        assertTrue(warnings.stream().anyMatch(w ->
                "OUT_OF_BOUNDS".equals(w.getType()) && w.getSides() != null));
    }
}
