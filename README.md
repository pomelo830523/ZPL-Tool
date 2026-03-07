# ZPL Tool

ZPL ↔ PNG 雙向轉換工具，完全本地運行，不依賴任何雲端 API。

- **Frontend**：Angular 15（standalone components）
- **Backend**：Spring Boot 3.2 + Java 17
- **ZPL 渲染**：Java AWT（文字／圖形）+ [ZXing](https://github.com/zxing/zxing)（條碼）
- **OCR**：[Tess4J 5.8](https://github.com/nguyenq/tess4j)（Tesseract 5，選用）

---

## 功能

### Tab 1 — ZPL → PNG

| 功能 | 說明 |
|------|------|
| ZPL 渲染 | 支援常用 ZPL II 指令，即時預覽 PNG |
| 語法高亮 | 編輯器即時語法高亮（`^XA/^XZ` 紅、指令青、`^FD` 資料綠、數字橙、`~XX` 紫），dark theme |
| 語法錯誤偵測 | 後端解析階段偵測不支援指令、未知指令及參數錯誤（`^GB`/`^A0`/`^CF`/`^BY`/`^BC`/`^B3` 等），以 `SYNTAX` 警告回報行號與原因 |
| 字型精準度 | Cap-height 基準線對齊、Font 0 內嵌 BarlowCondensed-Bold 字型並以 per-character advance width 精確對齊 Labelary 輸出、四方向旋轉 |
| 多種條碼 | Code 128（`^BC`，預設 Code128-B）、Code 39（`^B3`）、QR Code（`^BQ`） |
| 精確條碼寬度 | 兩步驟編碼：先算模組數再乘以 `barcodeModuleWidth`，確保條寬完全正確 |
| 點陣圖形 | `~DG`（存入印表機 RAM）+ `^XG`（呼叫回放） |
| 反轉欄位 | `^FR`：以 XOR 模式繪製，黑底白字／白底黑字正確互換（用於巢狀圖形） |
| 標籤設定 | 可調整寬度、高度（英吋）與解析度（203 / 300 / 600 DPI） |
| 問題偵測 | 自動偵測欄位**超出邊界**與**重疊**，附詳細說明 |
| Debug 模式 | 一鍵切換：在圖片上標示問題區域（紅色＝超出邊界、橘色＝重疊、藍色＝條碼間距太小） |
| 可調閾值 | 重疊判定閾值（dots）可自由設定，避免誤報 |
| 下載 PNG | 支援下載一般圖片或 Debug 標註圖片 |

### Tab 2 — PNG → ZPL

| 功能 | 說明 |
|------|------|
| 條碼偵測 | ZXing 解碼 → `^BC` / `^B3` / `^BQ` |
| 形狀偵測 | Connected Components + 矩形分類 → `^GB`（實心、空心、線條） |
| 文字 OCR | Tess4J（需自備 tessdata）→ `^A0^FD` |
| 殘留保真 | 未辨識像素編碼為 `~DG` hex + `^XG` 還原，確保不失真 |
| 偵測預覽 | 彩色標注圖：青色＝條碼、綠色＝形狀、橘色＝文字 |
| 一鍵送出 | 「送至 ZPL 預覽器」按鈕直接切換 Tab 並填入生成的 ZPL |

---

## 實測截圖

> Debug 模式下同時偵測到**重疊**（橘色）與**超出邊界**（紅色）與**條碼間距太小**（藍色），並在圖片上標示位置。

![重疊與超出邊界偵測實測](image/Test_Overlap_Boundary_Detection.png)

---

## 專案結構

```
ZPLViewer/
├── backend/                          # Spring Boot
│   └── src/main/java/com/zplviewer/
│       ├── ZplViewerApplication.java
│       ├── config/
│       │   ├── WebConfig.java        # CORS 設定
│       │   └── FontConfig.java       # 載入 BarlowCondensed-Bold.ttf（啟動時 fail-fast）
│       ├── controller/
│       │   ├── ZplController.java    # POST /api/zpl/convert
│       │   └── PngController.java    # POST /api/png/to-zpl
│       ├── model/
│       │   ├── ZplRequest.java
│       │   ├── ConvertResponse.java
│       │   ├── RenderWarning.java
│       │   ├── PngToZplRequest.java  # image / threshold / minShapeDots / tessDataPath
│       │   └── PngToZplResponse.java # zpl / previewImage
│       ├── service/
│       │   ├── ZplService.java       # 協調渲染流程
│       │   ├── ZplRenderer.java      # 核心渲染器（含 ~DG / ^XG / ^FR / 語法警告）
│       │   └── PngToZplService.java  # PNG→ZPL 分析引擎
│       └── util/
│           └── CharWidthMeasurer.java  # 從 Labelary PNG 量測字元 advance width 的獨立工具
└── frontend/                         # Angular 15
    └── src/app/
        ├── app.component.ts          # 雙頁籤元件邏輯（含語法高亮 tokenizer）
        ├── app.component.html
        ├── app.component.css
        └── ../../styles.css          # 全域語法高亮色彩（.hl-*）
```

---

## 快速開始

### 需求

| 工具 | 版本 |
|------|------|
| Java | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| Angular CLI | 15+ |

### 啟動後端

```bash
cd backend
mvn spring-boot:run
```

服務啟動於 `http://localhost:8080`

### 啟動前端

```bash
cd frontend
npm install
npm start
```

開啟瀏覽器前往 `http://localhost:4200`

---

## OCR 設定（選用）

文字偵測（PNG → ZPL）需要 Tesseract 語言資料：

1. 下載 [`eng.traineddata`](https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata)
2. 放置於本機目錄，例如 `C:\tessdata\`
3. 在「PNG → ZPL」頁籤的「tessdata 路徑」欄位填入該路徑

> 若不填寫，OCR 步驟會被略過，文字區塊改由殘留 `~DG` 保留。

---

## API

### `POST /api/zpl/convert` — ZPL → PNG

**Request body（JSON）：**

```json
{
  "zpl": "^XA^FO50,50^A0N,50,50^FDHello World^FS^XZ",
  "width": 4,
  "height": 6,
  "dpmm": 8,
  "debug": false,
  "overlapThresholdDots": 5
}
```

| 欄位 | 型別 | 預設 | 說明 |
|------|------|------|------|
| `zpl` | string | — | ZPL 代碼內容 |
| `width` | double | 4.0 | 標籤寬度（英吋） |
| `height` | double | 6.0 | 標籤高度（英吋） |
| `dpmm` | int | 8 | 解析度（8＝203 DPI，12＝300 DPI，24＝600 DPI） |
| `debug` | boolean | false | `true` 時回傳的圖片會疊加問題標註 |
| `overlapThresholdDots` | int | 5 | 重疊閾值（dots）；交集的寬與高同時超過此值才視為重疊 |

**Response body（JSON）：**

```json
{
  "image": "<Base64 PNG>",
  "warnings": [
    {
      "type": "SYNTAX",
      "fieldA": "^GC",
      "detail": "繪製圓形（^GC），將被略過",
      "command": "GC",
      "line": 3
    },
    {
      "type": "OUT_OF_BOUNDS",
      "fieldA": "CODE128: 123456789",
      "detail": "RIGHT 超出 23 dots",
      "sides": "RIGHT",
      "excessDots": 23,
      "boundsA": [50, 200, 835, 100]
    },
    {
      "type": "OVERLAP",
      "fieldA": "TEXT: Hello World",
      "fieldB": "CODE128: 123456789",
      "detail": "重疊區域 200×30 dots",
      "boundsA": [50, 50, 300, 60],
      "boundsB": [50, 100, 835, 100],
      "intersect": [50, 100, 300, 30]
    }
  ]
}
```

**警告類型（`type`）：**

| type | 說明 | UI 顯示 |
|------|------|---------|
| `SYNTAX` | 不支援、未知指令或參數錯誤 | 紫色，含行號 |
| `OUT_OF_BOUNDS` | 欄位超出標籤邊界 | 紅色，Debug 標紅框 |
| `OVERLAP` | 兩欄位重疊超過閾值 | 橘色，Debug 標橘框 |
| `BARCODE_GAP` | 相鄰條碼水平間距不足 | 藍色，Debug 標藍框 |

### `POST /api/png/to-zpl` — PNG → ZPL

**Request body（JSON）：**

```json
{
  "image": "<Base64 PNG 或 data URI>",
  "threshold": 128,
  "minShapeDots": 20,
  "tessDataPath": "C:/tessdata"
}
```

| 欄位 | 型別 | 預設 | 說明 |
|------|------|------|------|
| `image` | string | — | Base64 編碼的 PNG（可含 `data:image/png;base64,` 前綴） |
| `threshold` | int | 128 | 灰階二值化閾值（0–255），低於此值視為黑色 |
| `minShapeDots` | int | 20 | 形狀偵測最小面積（px），過濾雜訊 |
| `tessDataPath` | string | `C:/tessdata` | Tesseract 語言資料目錄，留空則跳過 OCR |

**Response body（JSON）：**

```json
{
  "zpl": "~DGR:ZPLV0001.GRF,...\n^XA\n...\n^XZ\n",
  "previewImage": "<Base64 PNG 偵測標注圖>"
}
```

---

## 支援的 ZPL 指令

| 指令 | 說明 |
|------|------|
| `^XA` / `^XZ` | 標籤開始／結束 |
| `^FO{x},{y}` | 欄位原點（Field Origin，絕對座標） |
| `^FT{x},{y}` | 欄位原點（Field Top，同 ^FO） |
| `^FR` | 欄位反轉（Field Reverse）：以 XOR 模式繪製，正確實作巢狀圖形反色 |
| `^A0{o},{h},{w}` | CG Triumvirate 可縮放字型（N／R／I／B 四種方向） |
| `^AA`–`^AZ` | 點陣字型 A–Z（等寬，四種方向） |
| `^CF{font},{h}` | 變更預設字型與高度 |
| `^FD...^FS` | 欄位資料 |
| `^BY{mw},,{h}` | 條碼預設值（模組寬、高） |
| `^BC{o},{h},{text}` | Code 128 條碼（預設 Code128-B，符合 ZPL 規格） |
| `^B3{o},{h},{text}` | Code 39 條碼 |
| `^BQ{o},{model},{mag}` | QR Code |
| `^GB{w},{h},{thick},{color},{round}` | 圖形方框／線條（含圓角） |
| `~DG{name},{total},{bpr},{hex}` | 將點陣圖形存入印表機 RAM |
| `^XG{name},{mx},{my}` | 呼叫已存入的點陣圖形 |
| `^LH` / `^PW` / `^LL` | 標籤位置／寬度／長度（解析但不重設畫布） |

**不支援（觸發 SYNTAX 警告）：**

| 指令 | 說明 |
|------|------|
| `^GF` | 內嵌點陣圖形（binary／ASCII85 編碼格式），圖形不顯示 |
| `^FH` | Hex 欄位資料編碼，特殊字元可能顯示錯誤 |
| `^GC` | 繪製圓形，將被略過 |
| `^GE` | 繪製橢圓，將被略過 |

---

## ZPL 語法高亮

編輯器採用 **textarea overlay** 模式：`div.highlight-layer`（語法高亮，`pointer-events: none`）疊加於 `textarea.editor-layer`（透明文字，保留游標與選取）之上，兩者由 JS 同步捲動。

### 配色（Catppuccin Mocha）

| 顏色 | Token |
|------|-------|
| 紅（`#f38ba8`）粗體 | `^XA` / `^XZ` 標籤邊界 |
| 青（`#89dceb`） | `^XX` 指令名稱 |
| 紫（`#cba6f7`） | `~XX` tilde 指令 |
| 藍（`#89b4fa`） | `^FD` / `^FS` 資料包裹符 |
| 綠（`#a6e3a1`） | `^FD...^FS` 欄位字串資料 |
| 淺灰（`#cdd6f4`） | 指令參數 |
| 橙（`#fab387`） | 數字 |
| 暗灰（`#585b70`） | `~DG` hex 資料（長行，降低視覺干擾） |

---

## 問題偵測機制

渲染完成後，`ZplRenderer.analyze()` 對所有欄位的 BoundingBox 做三項檢查：

### 語法錯誤（SYNTAX）

解析階段即時偵測，不需等到渲染結束：

- **不支援指令**：`^GF`、`^FH`、`^GC`、`^GE` 等，給出具體說明
- **未知指令**：不在已知清單內的 `^XX` 指令（僅警告一次，避免重複）
- **參數驗證**：`^GB` 寬高厚度 < 1、`^A0`/`^CF` 字高 < 1、`^BY` 模組寬 < 1、`^BC`/`^B3` 無效方向等

每條 SYNTAX 警告附帶 `command`（指令名稱）與 `line`（行號，1-based）。

### 超出邊界（OUT_OF_BOUNDS）

任一欄位的邊界超出標籤範圍即觸發，並標示超出的邊（LEFT／TOP／RIGHT／BOTTOM）及超出 dots 數。

### 重疊（OVERLAP）

對所有欄位兩兩比對交集矩形。當交集的**寬度 AND 高度**同時超過 `overlapThresholdDots` 時才觸發警告，避免因浮點誤差或刻意設計（如邊框）產生誤報。

### 條碼間距不足（BARCODE_GAP）

對所有條碼欄位兩兩比對水平間距。當兩個條碼在 Y 軸有重疊且水平間距小於 `minBarcodeGapMm`（預設 5 mm）時觸發警告，防止印表機讀取錯誤。

### Debug Overlay 顏色說明

| 顏色 | 意義 |
|------|------|
| 藍色實線 | 標籤邊界 |
| 紅色虛線框 ＋ 半透明紅填色 | 超出邊界的欄位（取可見部分） |
| 橘色虛線框 ＋ 半透明橘填色 | 重疊的欄位對及其交集區 |
| 藍色虛線框 ＋ 半透明藍填色 | 條碼間距不足的欄位對及其間距區 |

---

## 渲染精準度說明

### 字型高度與基準線

ZPL `^A0N,{h}` 中的 `h` 代表大寫字母（Cap Height），而非 Java em-size。渲染器以 GlyphVector 直接測量 `'H'` 的視覺高度（`capHpx`），並以 `baseline = fieldOriginY + capHpx` 定位，使大寫字母頂端精確對齊 `fieldOriginY`。

### Font 0 寬度修正

ZPL Font 0（CG Triumvirate Bold）為 Zebra 專有字型，開源替代方案採用 **BarlowCondensed-Bold**（Google Fonts）作為替代字型，並以 **per-character advance width lookup table**（`CG_WIDTHS`）對每個字元個別縮放。

- 量測方式：使用 `CharWidthMeasurer` 工具，從 Labelary 渲染的 PNG（`^A0N,30,24`）偵測各字元的像素起始位置，計算 advance width（`segs[i+1].start − segs[i].start`），轉換成 `ratio = dots / fontHeight`。
- 套用方式：`charWidth = CG_WIDTHS[c] × effectiveW / CG_REF_RATIO`，再透過 `AffineTransform.scale(charXScale, 1.0)` 逐字元縮放繪製。
- 覆蓋範圍：A–Z、a–z、0–9 及常用標點符號，確保與 Labelary 輸出逐字元寬度一致。

若要重新量測，可執行：
```bash
cd backend
mvn exec:java "-Dexec.mainClass=com.zplviewer.util.CharWidthMeasurer" "-Dexec.args=../image/labelary_font.png"
```

### 條碼模組寬度

ZXing 的 `width` 參數為目標像素寬，實際條寬會因模組數取整而偏差。渲染器採**兩步驟**策略：

1. 以 `width=1` 編碼，取得精確模組數（`matrix.getWidth()`）
2. 以 `moduleCount × barcodeModuleWidth` 重新編碼，確保每個條模組寬度恰好等於 `barcodeModuleWidth`

### `^FR` XOR 語義

ZPL `^FR` 使用 XOR 繪製：黑底上的黑色矩形→白色，白底上的黑色矩形→黑色。渲染器對應使用 `g.setXORMode(Color.WHITE)` + `g.setPaintMode()`，正確還原巢狀方框（如公司 Logo）的框線效果。

---

## 開發注意事項

- 後端 CORS 預設僅允許 `http://localhost:4200`，如需修改請編輯 `WebConfig.java`
- 標籤尺寸換算：`dots = 英吋 × 25.4 × dpmm`（例如 4" × 8dpmm = 812 dots）
- `ZplRenderer` 每次請求建立新實例，非 singleton，天然執行緒安全
- 渲染流程：`render()` → `analyze()` → `applyDebugOverlay()`（選用）→ `toPng()`
- 字型檔 `BarlowCondensed-Bold.ttf` 位於 `backend/src/main/resources/fonts/`，啟動時由 `FontConfig` 載入；若檔案遺失，應用程式會在啟動時立即拋出 `IllegalStateException`
- 後端單元測試以 JaCoCo 強制 ≥ 60% instruction 覆蓋率，執行 `mvn verify` 可同時跑測試並產生覆蓋率報告（`target/site/jacoco/index.html`）
- 語法高亮的 `.hl-*` CSS 規則必須放在全域 `styles.css`（非元件 CSS），原因是 Angular view encapsulation 不對 `[innerHTML]` 插入的 DOM 套用元件 scoped 樣式
