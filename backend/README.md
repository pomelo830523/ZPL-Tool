# ZPL Viewer — Backend

Spring Boot 後端服務，提供兩個核心功能：

1. **ZPL → PNG**：本地渲染 ZPL II 標籤並回傳 Base64 PNG，附帶智慧警告分析
2. **PNG → ZPL**：將圖片反向轉換為 ZPL 指令（條碼偵測 + OCR + 殘差補丁）

---

## 技術規格

| 項目 | 內容 |
|---|---|
| 框架 | Spring Boot 3.2.3 |
| Java | 17+ |
| 條碼引擎 | ZXing 3.5.3（Code128 / Code39 / QR Code / DataMatrix） |
| OCR | Tess4J 5.8.0（選用，需 `eng.traineddata`） |
| 圖形渲染 | Java AWT |
| 測試 | JUnit 5 + MockMvc |
| 覆蓋率 | JaCoCo（instruction coverage ≥ 60%） |

---

## 快速啟動

```bash
cd backend
mvn spring-boot:run
```

服務預設監聽 `http://localhost:8080`

---

## API 端點

### POST `/api/zpl/convert`

將 ZPL 字串渲染為 PNG 圖片。

**Request Body**

```json
{
  "zpl": "^XA^FO100,100^A0N,40^FDHello World^FS^XZ",
  "width": 4.0,
  "height": 6.0,
  "dpmm": 8,
  "debug": false,
  "overlapThresholdMm": 0,
  "defaultBarcodeHeight": 100
}
```

| 欄位 | 類型 | 預設 | 說明 |
|---|---|---|---|
| `zpl` | string | — | ZPL II 指令字串（必填） |
| `width` | double | 4.0 | 標籤寬度（英寸） |
| `height` | double | 6.0 | 標籤高度（英寸） |
| `dpmm` | int | 8 | 解析度（dots per mm）；常見值：6 / 8 / 12 / 24 |
| `debug` | boolean | false | `true` 時在圖片上疊加警告標註 |
| `overlapThresholdMm` | double | 0 | 重疊閾值（mm），低於此值的交集不報告 |
| `defaultBarcodeHeight` | int | 100 | 條碼預設高度（dots），可被 `^BY` 覆蓋 |

**Response Body**

```json
{
  "image": "<base64 PNG>",
  "warnings": [
    {
      "type": "OUT_OF_BOUNDS",
      "fieldA": "TEXT: Hello",
      "detail": "RIGHT 超出安全邊界 2.5 mm（安全距離 3 mm）",
      "sides": "RIGHT",
      "excessDots": 20,
      "boundsA": [740, 100, 80, 32]
    }
  ]
}
```

**警告類型**

| type | 說明 | 顏色（debug=true） |
|---|---|---|
| `OUT_OF_BOUNDS` | 欄位超出 3 mm 安全邊界 | 紅色虛線框 |
| `OVERLAP` | 兩個欄位互相重疊 | 橘色虛線框 + 交集填色 |
| `BARCODE_GAP` | 相鄰條碼水平間距不足 | 藍色虛線框 + gap 填色 |

---

### POST `/api/png/to-zpl`

將 PNG 圖片反向轉換為 ZPL 字串。

**Request Body**

```json
{
  "image": "<base64 PNG>",
  "threshold": 128,
  "minShapeDots": 10,
  "tessDataPath": "/usr/share/tessdata"
}
```

| 欄位 | 類型 | 預設 | 說明 |
|---|---|---|---|
| `image` | string | — | Base64 編碼的 PNG（必填） |
| `threshold` | int | 128 | 二值化灰階閾值（0–255） |
| `minShapeDots` | int | 10 | 忽略小於此面積的形狀（dots²） |
| `tessDataPath` | string | null | Tesseract `tessdata` 目錄，留空則跳過 OCR |

---

## 支援的 ZPL 指令

| 指令 | 功能 |
|---|---|
| `^XA` / `^XZ` | 標籤開始 / 結束（重置狀態） |
| `^FO x,y` | 設定欄位原點 |
| `^FT x,y` | 平移欄位原點 |
| `^A0 o,h,w` | CG Triumvirate 字型（可縮放粗體） |
| `^AA`–`^AZ` | 點陣字型 A–Z |
| `^CF f,h` | 更改預設字型 |
| `^FD … ^FS` | 欄位資料 |
| `^FR` | 欄位反色（前/背景互換） |
| `^BY mw,,h` | 條碼預設模組寬度與高度 |
| `^BC o,h,p` | Code 128 條碼 |
| `^B3 o,e,h` | Code 39 條碼 |
| `^BQ o,m,mag` | QR Code |
| `^BX o,h,q` | Data Matrix |
| `^BE` / `^BU` | EAN / UPC（退化為 Code128） |
| `^GB w,h,t,c,r` | 圖形方框（支援圓角、填色、白色） |
| `~DG filename,b,bpr,hex` | 儲存點陣圖形 |
| `^XG filename,mx,my` | 叫出已儲存圖形 |

---

## 設定

`src/main/resources/application.properties`

```properties
# 條碼間最小水平間距（mm），0 = 停用檢查
zpl.barcode.min-horizontal-gap-mm=5
```

---

## 執行測試

```bash
# 執行測試並產生覆蓋率報告
mvn test

# 報告位置
open target/site/jacoco/index.html
```

### 測試結構

```
src/test/java/com/zplviewer/
├── controller/
│   └── ZplControllerTest.java   # MockMvc 整合測試（5 cases）
├── model/
│   ├── ZplRequestTest.java      # 模型驗證（6 cases）
│   ├── RenderWarningTest.java   # 模型 getters/setters（2 cases）
│   └── ConvertResponseTest.java # 建構子（2 cases）
└── service/
    ├── ZplRendererTest.java     # 渲染器核心邏輯（50 cases）
    └── ZplServiceTest.java      # 服務層（6 cases）
```

**覆蓋項目（ZplRendererTest）**

- Render 生命週期（render → analyze → applyDebugOverlay → toPng）
- 文字渲染：四種方向（N/R/I/B）、^CF、^FR、顯式字寬、連字號三倍寬
- 條碼渲染：Code128（含/不含 HRI）、Code39、QR Code、DataMatrix、^BE / ^BU 退化
- 圖形方框：填色、輪廓、白色、圓角、^FR 反色
- ~DG / ^XG：正常儲存與叫出、畸形資料防護
- analyze：OUT_OF_BOUNDS（多邊）、OVERLAP（含閾值過濾）、BARCODE_GAP（含停用/啟用）
- applyDebugOverlay：三種 warning type、零寬 gap 防護、完全在標籤外的欄位防護

---

## 專案結構

```
backend/src/main/java/com/zplviewer/
├── ZplViewerApplication.java       # Spring Boot 入口
├── config/
│   └── WebConfig.java              # CORS 設定（允許 localhost:4200）
├── controller/
│   ├── ZplController.java          # POST /api/zpl/convert
│   └── PngController.java          # POST /api/png/to-zpl
├── model/
│   ├── ZplRequest.java             # ZPL 轉換請求
│   ├── ConvertResponse.java        # ZPL 轉換回應
│   ├── RenderWarning.java          # 單一警告（含 BoundingBox）
│   ├── PngToZplRequest.java        # PNG 轉換請求
│   └── PngToZplResponse.java       # PNG 轉換回應
└── service/
    ├── ZplRenderer.java            # 核心 ZPL 渲染器
    ├── ZplService.java             # ZPL 轉換業務邏輯
    └── PngToZplService.java        # PNG→ZPL 轉換邏輯
```
