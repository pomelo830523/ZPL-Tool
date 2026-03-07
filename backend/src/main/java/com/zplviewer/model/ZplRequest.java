package com.zplviewer.model;

public class ZplRequest {

    private String zpl;
    private double width  = 4.0;
    private double height = 6.0;
    private int    dpmm   = 8;

    /** 是否回傳 Debug 標註圖（超出/重疊區域以顏色標記） */
    private boolean debug = false;

    /**
     * 重疊判定閾值（mm）。
     * 兩個欄位的交集寬度 AND 高度都超過此值時才視為重疊。
     * 預設為 0，任何交集都觸發警告。
     */
    private double overlapThresholdMm = 0;

    /**
     * 條碼預設高度（dots）。對應 ZplRenderer 的 barcodeHeight 初始值。
     * ZPL 指令 ^BY 可覆蓋此預設值。
     */
    private int defaultBarcodeHeight = 100;

    public String getZpl()    { return zpl; }
    public void setZpl(String zpl) { this.zpl = zpl; }

    public double getWidth()  { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public int getDpmm()      { return dpmm; }
    public void setDpmm(int dpmm) { this.dpmm = dpmm; }

    public boolean isDebug()  { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }

    public double getOverlapThresholdMm() { return overlapThresholdMm; }
    public void setOverlapThresholdMm(double overlapThresholdMm) {
        this.overlapThresholdMm = Math.max(0, overlapThresholdMm);
    }

    public int getDefaultBarcodeHeight() { return defaultBarcodeHeight; }
    public void setDefaultBarcodeHeight(int defaultBarcodeHeight) {
        this.defaultBarcodeHeight = Math.max(10, defaultBarcodeHeight);
    }
}
