package com.zplviewer.model;

public class ZplRequest {

    private String zpl;
    private double width = 4.0;
    private double height = 6.0;
    private int dpmm = 8;

    public String getZpl() { return zpl; }
    public void setZpl(String zpl) { this.zpl = zpl; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public int getDpmm() { return dpmm; }
    public void setDpmm(int dpmm) { this.dpmm = dpmm; }
}
