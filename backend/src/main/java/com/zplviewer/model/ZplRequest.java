package com.zplviewer.model;

public class ZplRequest {

    private String zpl;
    private double width  = 4.0;
    private double height = 6.0;
    private int    dpmm   = 8;

    /** 是否回傳 Debug 標註圖（超出/重疊區域以顏色標記） */
    private boolean debug = false;

    /**
     * 重疊判定閾值（dots）。
     * 兩個欄位的交集寬度 AND 高度都超過此值時才視為重疊。
     * 預設 5 dots，設為 0 則任何交集都觸發警告。
     */
    private int overlapThresholdDots = 5;

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

    public int getOverlapThresholdDots() { return overlapThresholdDots; }
    public void setOverlapThresholdDots(int overlapThresholdDots) {
        this.overlapThresholdDots = Math.max(0, overlapThresholdDots);
    }
}
