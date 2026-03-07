package com.zplviewer.service;

import com.zplviewer.model.ConvertResponse;
import com.zplviewer.model.RenderWarning;
import com.zplviewer.model.ZplRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Service
public class ZplService {

    @Value("${zpl.barcode.min-horizontal-gap-mm:5}")
    private double minBarcodeGapMm;

    /**
     * 將 ZPL 轉為 PNG，同時分析並回傳渲染警告。
     *
     * 流程：render → analyze → (optional) applyDebugOverlay → toPng
     */
    public ConvertResponse convertToPng(ZplRequest request) throws Exception {
        int dpmm       = request.getDpmm();
        int widthDots  = (int) Math.round(request.getWidth()  * 25.4 * dpmm);
        int heightDots = (int) Math.round(request.getHeight() * 25.4 * dpmm);
        int minBarcodeGapDots = (int) Math.round(minBarcodeGapMm * dpmm);

        ZplRenderer renderer = new ZplRenderer(widthDots, heightDots, dpmm,
                request.getDefaultBarcodeHeight(), minBarcodeGapDots);

        // Step 1：渲染（同時建立 BoundingBox 記錄）
        renderer.render(request.getZpl());

        // Step 2：分析警告
        List<RenderWarning> warnings = renderer.analyze(request.getOverlapThresholdMm());

        // Step 3（選用）：在圖片上疊加 Debug 標註
        if (request.isDebug()) {
            renderer.applyDebugOverlay(warnings);
        }

        // Step 4：輸出 PNG
        String base64 = Base64.getEncoder().encodeToString(renderer.toPng());
        return new ConvertResponse(base64, warnings);
    }
}
