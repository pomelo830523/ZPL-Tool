# ZPL Viewer

一個全端 ZPL 標籤預覽工具，在瀏覽器中即時將 ZPL 代碼渲染為 PNG 圖片。

- **Frontend**：Angular 17（standalone components）
- **Backend**：Spring Boot 3.2 + Java 17
- **ZPL 渲染**：Java AWT（文字／圖形）+ [ZXing](https://github.com/zxing/zxing)（條碼）
- **完全本地**：不依賴任何雲端 API，可在離線環境使用

---

## 功能

| 功能 | 說明 |
|------|------|
| ZPL 渲染 | 支援常用 ZPL II 指令，即時預覽 PNG |
| 多種條碼 | Code 128（`^BC`）、Code 39（`^B3`）、QR Code（`^BQ`） |
| 標籤設定 | 可調整寬度、高度（英吋）與解析度（203 / 300 / 600 DPI） |
| 問題偵測 | 自動偵測欄位**超出邊界**與**重疊**，附詳細說明 |
| Debug 模式 | 一鍵切換：在圖片上標示問題區域（紅色＝超出、橘色＝重疊） |
| 可調閾值 | 重疊判定閾值（dots）可自由設定，避免誤報 |
| 下載 PNG | 支援下載一般圖片或 Debug 標註圖片 |

---

## 實測截圖

> Debug 模式下同時偵測到**重疊**（橘色）與**超出邊界**（紅色），並在圖片上標示位置。

![重疊與超出邊界偵測實測](image/Test_Overlap_Boundary_Detection.png)

---

## 專案結構

```
ZPLViewer/
├── backend/                          # Spring Boot
│   └── src/main/java/com/zplviewer/
│       ├── ZplViewerApplication.java
│       ├── config/WebConfig.java     # CORS 設定
│       ├── controller/ZplController.java
│       ├── model/
│       │   ├── ZplRequest.java       # 請求參數（含 debug、閾值）
│       │   ├── ConvertResponse.java  # 回應（圖片 + 警告清單）
│       │   └── RenderWarning.java    # 單一警告（含 BoundingBox）
│       └── service/
│           ├── ZplService.java       # 協調渲染流程
│           └── ZplRenderer.java      # 核心渲染器
└── frontend/                         # Angular 17
    └── src/app/
        ├── app.component.ts
        ├── app.component.html
        └── app.component.css
```

---

## 快速開始

### 需求

| 工具 | 版本 |
|------|------|
| Java | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| Angular CLI | 17+ |

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

## API

### `POST /api/zpl/convert`

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

---

## 支援的 ZPL 指令

| 指令 | 說明 |
|------|------|
| `^XA` / `^XZ` | 標籤開始／結束 |
| `^FO{x},{y}` | 欄位原點（Field Origin） |
| `^A0{orientation},{h},{w}` | 可縮放字型（N／R／I／B 四種方向） |
| `^CF{font},{h}` | 變更預設字型 |
| `^FD...^FS` | 欄位資料 |
| `^BY{mw},,{h}` | 條碼預設值（模組寬、高） |
| `^BC{o},{h},{text}` | Code 128 條碼 |
| `^B3{o},{h},{text}` | Code 39 條碼 |
| `^BQ{o},{model},{mag}` | QR Code |
| `^GB{w},{h},{thick},{color},{round}` | 圖形方框／線條 |
| `^LH` / `^PW` / `^LL` | 標籤位置／寬度／長度（解析但不重設畫布） |

---

## 問題偵測機制

渲染完成後，`ZplRenderer.analyze()` 對所有欄位的 BoundingBox 做兩項檢查：

### 超出邊界（OUT_OF_BOUNDS）

任一欄位的邊界超出標籤範圍即觸發，並標示超出的邊（LEFT／TOP／RIGHT／BOTTOM）及超出 dots 數。

### 重疊（OVERLAP）

對所有欄位兩兩比對交集矩形。當交集的**寬度 AND 高度**同時超過 `overlapThresholdDots` 時才觸發警告，避免因浮點誤差或刻意設計（如邊框）產生誤報。

### Debug Overlay 顏色說明

| 顏色 | 意義 |
|------|------|
| 藍色實線 | 標籤邊界 |
| 紅色虛線框 ＋ 半透明紅填色 | 超出邊界的欄位（取可見部分） |
| 橘色虛線框 ＋ 半透明橘填色 | 重疊的欄位對及其交集區 |

---

## 開發注意事項

- 後端 CORS 預設僅允許 `http://localhost:4200`，如需修改請編輯 `WebConfig.java`
- 標籤尺寸換算：`dots = 英吋 × 25.4 × dpmm`（例如 4" × 8dpmm = 812 dots）
- `ZplRenderer` 每次請求建立新實例，非 singleton，天然執行緒安全
- 渲染流程：`render()` → `analyze()` → `applyDebugOverlay()`（選用）→ `toPng()`
