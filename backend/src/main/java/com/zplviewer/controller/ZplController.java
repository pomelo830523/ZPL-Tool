package com.zplviewer.controller;

import com.zplviewer.model.ZplRequest;
import com.zplviewer.service.ZplService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/zpl")
public class ZplController {

    private final ZplService zplService;

    public ZplController(ZplService zplService) {
        this.zplService = zplService;
    }

    @PostMapping("/convert")
    public ResponseEntity<?> convert(@RequestBody ZplRequest request) {
        if (request.getZpl() == null || request.getZpl().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "ZPL 內容不可為空"));
        }
        try {
            String base64Image = zplService.convertToPng(request);
            return ResponseEntity.ok(Map.of("image", base64Image));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "ZPL 轉換失敗: " + e.getMessage()));
        }
    }
}
