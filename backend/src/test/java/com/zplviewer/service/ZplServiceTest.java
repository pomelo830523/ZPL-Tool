package com.zplviewer.service;

import com.zplviewer.model.ConvertResponse;
import com.zplviewer.model.ZplRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class ZplServiceTest {

    private ZplService service;

    @BeforeEach
    void setUp() {
        service = new ZplService();
        ReflectionTestUtils.setField(service, "minBarcodeGapMm", 5.0);
    }

    private ZplRequest request(String zpl) {
        ZplRequest req = new ZplRequest();
        req.setZpl(zpl);
        req.setWidth(4.0);
        req.setHeight(6.0);
        req.setDpmm(8);
        req.setDebug(false);
        return req;
    }

    @Test
    void convertToPng_simpleText_returnsBase64Image() throws Exception {
        ZplRequest req = request("^XA^FO100,100^A0N,30^FDHello^FS^XZ");
        ConvertResponse resp = service.convertToPng(req);

        assertNotNull(resp);
        assertNotNull(resp.getImage());
        assertFalse(resp.getImage().isEmpty());
        assertNotNull(resp.getWarnings());
    }

    @Test
    void convertToPng_debugTrue_stillReturnsImage() throws Exception {
        ZplRequest req = request("^XA^FO100,100^A0N,30^FDDebug^FS^XZ");
        req.setDebug(true);
        ConvertResponse resp = service.convertToPng(req);

        assertNotNull(resp.getImage());
    }

    @Test
    void convertToPng_withOverlapWarning_debugTrue() throws Exception {
        ZplRequest req = request(
                "^XA"
                + "^FO500,500^GB400,300,300^FS"
                + "^FO500,500^GB400,300,300^FS"
                + "^XZ");
        req.setDebug(true);
        ConvertResponse resp = service.convertToPng(req);

        assertNotNull(resp.getImage());
        assertTrue(resp.getWarnings().stream()
                .anyMatch(w -> "OVERLAP".equals(w.getType())));
    }

    @Test
    void convertToPng_withCode128Barcode() throws Exception {
        ZplRequest req = request("^XA^FO200,200^BCN,100,Y^FD123456789^FS^XZ");
        ConvertResponse resp = service.convertToPng(req);

        assertNotNull(resp.getImage());
    }

    @Test
    void convertToPng_imageIsValidBase64() throws Exception {
        ZplRequest req = request("^XA^FO200,200^A0N,30^FDBase64Test^FS^XZ");
        ConvertResponse resp = service.convertToPng(req);

        // Base64 string must only contain valid characters
        String img = resp.getImage();
        assertTrue(img.matches("[A-Za-z0-9+/=]+"),
                "Image should be valid Base64");
    }

    @Test
    void convertToPng_differentDpmm() throws Exception {
        ZplRequest req = request("^XA^FO100,100^A0N,30^FDHi^FS^XZ");
        req.setDpmm(12);
        ConvertResponse resp = service.convertToPng(req);
        assertNotNull(resp.getImage());
    }
}
