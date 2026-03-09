package com.zplviewer.model;

public class ZplRequest {

    private String zpl;
    private double width  = 152.0;
    private double height = 102.0;
    private int    dpmm   = 8;

    /** 是否回傳 Debug 標註圖（超出/重疊區域以顏色標記） */
    private boolean debug = false;

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

    public int getDefaultBarcodeHeight() { return defaultBarcodeHeight; }
    public void setDefaultBarcodeHeight(int defaultBarcodeHeight) {
        this.defaultBarcodeHeight = Math.max(10, defaultBarcodeHeight);
    }
}
