package com.zplviewer.service;

import com.zplviewer.model.ZplRequest;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class ZplService {

    /**
     * Convert ZPL to PNG locally using ZplRenderer (Java AWT + ZXing).
     * No external API calls – works fully offline.
     *
     * @param request contains zpl string, label width/height (inches), dpmm
     * @return Base64-encoded PNG string
     */
    public String convertToPng(ZplRequest request) throws Exception {
        // Convert label dimensions from inches to dots
        // dots = inches * mm_per_inch * dots_per_mm
        int widthDots  = (int) Math.round(request.getWidth()  * 25.4 * request.getDpmm());
        int heightDots = (int) Math.round(request.getHeight() * 25.4 * request.getDpmm());

        ZplRenderer renderer = new ZplRenderer(widthDots, heightDots);
        byte[] pngBytes = renderer.renderToPng(request.getZpl());
        return Base64.getEncoder().encodeToString(pngBytes);
    }
}
