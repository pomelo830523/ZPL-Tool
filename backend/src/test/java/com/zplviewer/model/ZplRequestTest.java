package com.zplviewer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZplRequestTest {

    @Test
    void defaultValues() {
        ZplRequest req = new ZplRequest();
        assertEquals(4.0,  req.getWidth());
        assertEquals(6.0,  req.getHeight());
        assertEquals(8,    req.getDpmm());
        assertFalse(req.isDebug());
        assertEquals(0.0,  req.getOverlapThresholdMm());
        assertEquals(100,  req.getDefaultBarcodeHeight());
        assertNull(req.getZpl());
    }

    @Test
    void settersRoundtrip() {
        ZplRequest req = new ZplRequest();
        req.setZpl("^XA^XZ");
        req.setWidth(3.5);
        req.setHeight(5.0);
        req.setDpmm(12);
        req.setDebug(true);

        assertEquals("^XA^XZ", req.getZpl());
        assertEquals(3.5, req.getWidth());
        assertEquals(5.0, req.getHeight());
        assertEquals(12,  req.getDpmm());
        assertTrue(req.isDebug());
    }

    @Test
    void overlapThresholdMm_clampedToZero() {
        ZplRequest req = new ZplRequest();
        req.setOverlapThresholdMm(-5.0);
        assertEquals(0.0, req.getOverlapThresholdMm());
    }

    @Test
    void overlapThresholdMm_positiveAllowed() {
        ZplRequest req = new ZplRequest();
        req.setOverlapThresholdMm(2.5);
        assertEquals(2.5, req.getOverlapThresholdMm());
    }

    @Test
    void defaultBarcodeHeight_clampedToTen() {
        ZplRequest req = new ZplRequest();
        req.setDefaultBarcodeHeight(3);
        assertEquals(10, req.getDefaultBarcodeHeight());
    }

    @Test
    void defaultBarcodeHeight_positiveAllowed() {
        ZplRequest req = new ZplRequest();
        req.setDefaultBarcodeHeight(150);
        assertEquals(150, req.getDefaultBarcodeHeight());
    }
}
