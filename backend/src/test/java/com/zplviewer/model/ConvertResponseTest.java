package com.zplviewer.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConvertResponseTest {

    @Test
    void gettersReturnConstructorArgs() {
        List<RenderWarning> warnings = Collections.emptyList();
        ConvertResponse resp = new ConvertResponse("base64data", warnings);

        assertEquals("base64data", resp.getImage());
        assertSame(warnings, resp.getWarnings());
    }

    @Test
    void nullsAllowed() {
        ConvertResponse resp = new ConvertResponse(null, null);
        assertNull(resp.getImage());
        assertNull(resp.getWarnings());
    }
}
