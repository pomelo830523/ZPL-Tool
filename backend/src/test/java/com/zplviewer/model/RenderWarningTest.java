package com.zplviewer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderWarningTest {

    @Test
    void allGettersSetters() {
        RenderWarning w = new RenderWarning();

        w.setType("OUT_OF_BOUNDS");
        w.setFieldA("TEXT: hello");
        w.setFieldB("CODE128: 123");
        w.setDetail("超出邊界");
        w.setBoundsA(new int[]{10, 20, 30, 40});
        w.setBoundsB(new int[]{50, 60, 70, 80});
        w.setIntersect(new int[]{15, 25, 5, 5});
        w.setSides("RIGHT+BOTTOM");
        w.setExcessDots(8);

        assertEquals("OUT_OF_BOUNDS",   w.getType());
        assertEquals("TEXT: hello",     w.getFieldA());
        assertEquals("CODE128: 123",    w.getFieldB());
        assertEquals("超出邊界",         w.getDetail());
        assertArrayEquals(new int[]{10, 20, 30, 40}, w.getBoundsA());
        assertArrayEquals(new int[]{50, 60, 70, 80}, w.getBoundsB());
        assertArrayEquals(new int[]{15, 25,  5,  5}, w.getIntersect());
        assertEquals("RIGHT+BOTTOM",    w.getSides());
        assertEquals(8,                 w.getExcessDots());
    }

    @Test
    void defaultsAreNull() {
        RenderWarning w = new RenderWarning();
        assertNull(w.getType());
        assertNull(w.getFieldA());
        assertNull(w.getFieldB());
        assertNull(w.getBoundsA());
        assertNull(w.getBoundsB());
        assertNull(w.getIntersect());
        assertNull(w.getSides());
        assertEquals(0, w.getExcessDots());
    }
}
