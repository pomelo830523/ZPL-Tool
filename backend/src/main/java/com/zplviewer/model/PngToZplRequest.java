package com.zplviewer.model;

public class PngToZplRequest {
    private String image;           // Base64-encoded PNG
    private int threshold = 128;    // Grayscale binarization threshold (0-255)
    private int minShapeDots = 20;  // Min area (pixels) for shape to be detected as ^GB
    private String tessDataPath = "C:/tessdata"; // Path to tessdata directory (e.g. C:/tessdata)

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getThreshold() { return threshold; }
    public void setThreshold(int threshold) { this.threshold = threshold; }

    public int getMinShapeDots() { return minShapeDots; }
    public void setMinShapeDots(int minShapeDots) { this.minShapeDots = minShapeDots; }

    public String getTessDataPath() { return tessDataPath; }
    public void setTessDataPath(String tessDataPath) { this.tessDataPath = tessDataPath; }
}
