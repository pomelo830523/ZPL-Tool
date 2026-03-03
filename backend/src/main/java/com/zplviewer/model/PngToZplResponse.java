package com.zplviewer.model;

public class PngToZplResponse {
    private String zpl;           // Generated ZPL string
    private String previewImage;  // Base64 PNG with color-coded detection overlay

    public String getZpl() { return zpl; }
    public void setZpl(String zpl) { this.zpl = zpl; }

    public String getPreviewImage() { return previewImage; }
    public void setPreviewImage(String previewImage) { this.previewImage = previewImage; }
}
