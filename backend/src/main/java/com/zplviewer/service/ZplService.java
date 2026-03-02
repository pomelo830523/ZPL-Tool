package com.zplviewer.service;

import com.zplviewer.model.ConvertResponse;
import com.zplviewer.model.RenderWarning;
import com.zplviewer.model.ZplRequest;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Service
public class ZplService {

    /**
     * 將 ZPL 轉為 PNG，同時分析並回傳渲染警告。
     *
     * 流程：render → analyze → (optional) applyDebugOverlay → toPng
     */
    public ConvertResponse convertToPng(ZplRequest request) throws Exception {
        int widthDots  = (int) Math.round(request.getWidth()  * 25.4 * request.getDpmm());
        int heightDots = (int) Math.round(request.getHeight() * 25.4 * request.getDpmm());

        ZplRenderer renderer = new ZplRenderer(widthDots, heightDots);

        // Step 1：渲染（同時建立 BoundingBox 記錄）
        renderer.render(request.getZpl());

        // Step 2：分析警告（使用可調整的重疊閾值）
        List<RenderWarning> warnings = renderer.analyze(request.getOverlapThresholdDots());

        // Step 3（選用）：在圖片上疊加 Debug 標註
        if (request.isDebug()) {
            renderer.applyDebugOverlay(warnings);
        }

        // Step 4：輸出 PNG
        String base64 = Base64.getEncoder().encodeToString(renderer.toPng());
        return new ConvertResponse(base64, warnings);
    }
}
