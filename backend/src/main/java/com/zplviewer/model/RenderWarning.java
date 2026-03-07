package com.zplviewer.model;

/**
 * 單一渲染警告，包含用於 Debug Overlay 所需的 BoundingBox 資訊。
 */
public class RenderWarning {

    /** "OUT_OF_BOUNDS" | "OVERLAP" | "BARCODE_GAP" */
    private String type;

    /** 第一個欄位的描述，例如 "CODE128: 123456789" */
    private String fieldA;

    /** 第二個欄位的描述（僅 OVERLAP 使用） */
    private String fieldB;

    /** 人類可讀的說明文字 */
    private String detail;

    /** 欄位 A 的 BoundingBox [x, y, w, h]（dots） */
    private int[] boundsA;

    /** 欄位 B 的 BoundingBox [x, y, w, h]（僅 OVERLAP 使用） */
    private int[] boundsB;

    /** 重疊交集區域 [x, y, w, h]（僅 OVERLAP 使用） */
    private int[] intersect;

    /** 超出哪些邊，如 "RIGHT"、"BOTTOM"、"RIGHT+BOTTOM"（僅 OUT_OF_BOUNDS 使用） */
    private String sides;

    /** 超出邊界的最大 dots 數（僅 OUT_OF_BOUNDS 使用） */
    private int excessDots;

    // ── Getters / Setters ─────────────────────────────────────────────

    public String getType()    { return type; }
    public void setType(String type) { this.type = type; }

    public String getFieldA()  { return fieldA; }
    public void setFieldA(String fieldA) { this.fieldA = fieldA; }

    public String getFieldB()  { return fieldB; }
    public void setFieldB(String fieldB) { this.fieldB = fieldB; }

    public String getDetail()  { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public int[] getBoundsA()  { return boundsA; }
    public void setBoundsA(int[] boundsA) { this.boundsA = boundsA; }

    public int[] getBoundsB()  { return boundsB; }
    public void setBoundsB(int[] boundsB) { this.boundsB = boundsB; }

    public int[] getIntersect() { return intersect; }
    public void setIntersect(int[] intersect) { this.intersect = intersect; }

    public String getSides()   { return sides; }
    public void setSides(String sides) { this.sides = sides; }

    public int getExcessDots() { return excessDots; }
    public void setExcessDots(int excessDots) { this.excessDots = excessDots; }
}
