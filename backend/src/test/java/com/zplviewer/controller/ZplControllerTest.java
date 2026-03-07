package com.zplviewer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zplviewer.model.ConvertResponse;
import com.zplviewer.model.ZplRequest;
import com.zplviewer.service.ZplService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ZplController.class)
class ZplControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ZplService zplService;

    @Autowired
    ObjectMapper objectMapper;

    private String json(ZplRequest req) throws Exception {
        return objectMapper.writeValueAsString(req);
    }

    @Test
    void convert_emptyZpl_returns400() throws Exception {
        ZplRequest req = new ZplRequest();
        req.setZpl("");

        mockMvc.perform(post("/api/zpl/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void convert_nullZpl_returns400() throws Exception {
        ZplRequest req = new ZplRequest();
        req.setZpl(null);

        mockMvc.perform(post("/api/zpl/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convert_validZpl_returns200() throws Exception {
        ConvertResponse mockResp = new ConvertResponse("base64img", Collections.emptyList());
        Mockito.when(zplService.convertToPng(any())).thenReturn(mockResp);

        ZplRequest req = new ZplRequest();
        req.setZpl("^XA^FO100,100^A0N,30^FDHello^FS^XZ");

        mockMvc.perform(post("/api/zpl/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").value("base64img"))
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void convert_serviceThrowsException_returns500() throws Exception {
        Mockito.when(zplService.convertToPng(any()))
                .thenThrow(new RuntimeException("rendering failed"));

        ZplRequest req = new ZplRequest();
        req.setZpl("^XA^XZ");

        mockMvc.perform(post("/api/zpl/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("ZPL 轉換失敗: rendering failed"));
    }

    @Test
    void convert_blankZpl_returns400() throws Exception {
        ZplRequest req = new ZplRequest();
        req.setZpl("   ");

        mockMvc.perform(post("/api/zpl/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isBadRequest());
    }
}
