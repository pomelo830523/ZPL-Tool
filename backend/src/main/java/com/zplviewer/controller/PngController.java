package com.zplviewer.controller;

import com.zplviewer.model.PngToZplRequest;
import com.zplviewer.model.PngToZplResponse;
import com.zplviewer.service.PngToZplService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/png")
public class PngController {

    private final PngToZplService pngToZplService;

    public PngController(PngToZplService pngToZplService) {
        this.pngToZplService = pngToZplService;
    }

    @PostMapping("/to-zpl")
    public ResponseEntity<?> toZpl(@RequestBody PngToZplRequest request) {
        try {
            if (request.getImage() == null || request.getImage().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "image is required"));
            }

            // Strip data URI prefix if present
            String b64 = request.getImage();
            if (b64.contains(",")) b64 = b64.substring(b64.indexOf(',') + 1);

            byte[] pngBytes = Base64.getDecoder().decode(b64);

            PngToZplService.ConversionResult result = pngToZplService.convert(
                    pngBytes,
                    request.getThreshold(),
                    request.getMinShapeDots(),
                    request.getTessDataPath()
            );

            PngToZplResponse response = new PngToZplResponse();
            response.setZpl(result.zpl());
            response.setPreviewImage(pngToZplService.toPngBase64(result.overlayImage()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Conversion failed: " + e.getMessage()));
        }
    }
}
