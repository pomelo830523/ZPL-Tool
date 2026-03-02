package com.zplviewer.model;

import java.util.List;

/** API 回應：Base64 PNG 圖片 + 渲染警告清單 */
public class ConvertResponse {

    /** Base64 編碼的 PNG（debug=true 時為標註版） */
    private String image;

    /** 偵測到的問題清單（空代表無問題） */
    private List<RenderWarning> warnings;

    public ConvertResponse(String image, List<RenderWarning> warnings) {
        this.image    = image;
        this.warnings = warnings;
    }

    public String getImage()                  { return image; }
    public List<RenderWarning> getWarnings()  { return warnings; }
}
