import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface RenderWarning {
  type: 'OUT_OF_BOUNDS' | 'OVERLAP';
  fieldA: string;
  fieldB?: string;
  detail: string;
  sides?: string;
  excessDots?: number;
  boundsA?: number[];
  boundsB?: number[];
  intersect?: number[];
}

interface ConvertResponse {
  image: string;
  warnings: RenderWarning[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  zplInput = `^XA
^FO50,40^A0N,60,50^FDHello, ZPL World!^FS
^FO50,130^A0N,30,25^FDLocal rendering - no cloud needed^FS
^FO50,200^BY2^BCN,100,Y,N,N^FD123456789012^FS
^FO500,200^BQN,2,5^FDhttps://example.com^FS
^FO50,380^GB700,4,4^FS
^FO50,410^A0N,25,20^FDCode 39 barcode:^FS
^FO50,445^BY1^B3N,80,Y,N^FDHELLO-WORLD^FS
^XZ`;

  // ── Preview state ──────────────────────────────────────────────────
  imageBase64   = '';
  isLoading     = false;
  errorMessage  = '';
  warnings: RenderWarning[] = [];

  // ── Label settings ─────────────────────────────────────────────────
  labelWidth  = 4;
  labelHeight = 6;
  dpmm        = 8;

  // ── Analysis settings ──────────────────────────────────────────────
  /** 重疊閾值（dots）：交集的寬 AND 高都超過此值才視為重疊 */
  overlapThresholdDots = 5;

  // ── Debug mode ─────────────────────────────────────────────────────
  debugMode = false;

  private readonly apiUrl = 'http://localhost:8080/api/zpl/convert';

  constructor(private http: HttpClient) {}

  convertZpl(): void {
    if (!this.zplInput.trim()) {
      this.errorMessage = '請輸入 ZPL 代碼';
      return;
    }

    this.isLoading    = true;
    this.errorMessage = '';
    this.imageBase64  = '';
    this.warnings     = [];

    this.http.post<ConvertResponse>(this.apiUrl, {
      zpl:                  this.zplInput,
      width:                this.labelWidth,
      height:               this.labelHeight,
      dpmm:                 this.dpmm,
      debug:                this.debugMode,
      overlapThresholdDots: this.overlapThresholdDots
    }).subscribe({
      next: (response) => {
        this.imageBase64 = response.image;
        this.warnings    = response.warnings ?? [];
        this.isLoading   = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.error
          ?? '轉換失敗，請確認 ZPL 代碼是否正確，並確保後端服務已啟動 (port 8080)';
        this.isLoading = false;
      }
    });
  }

  /** 切換 Debug 模式；若已有圖片則立即重新轉換以套用/移除標註 */
  toggleDebug(): void {
    this.debugMode = !this.debugMode;
    if (this.imageBase64) this.convertZpl();
  }

  clearAll(): void {
    this.zplInput    = '';
    this.imageBase64 = '';
    this.errorMessage = '';
    this.warnings    = [];
    this.debugMode   = false;
  }

  downloadImage(): void {
    const link = document.createElement('a');
    link.href     = `data:image/png;base64,${this.imageBase64}`;
    link.download = this.debugMode ? 'label-debug.png' : 'label.png';
    link.click();
  }

  get warningCount(): number { return this.warnings.length; }
  get outOfBoundsCount(): number { return this.warnings.filter(w => w.type === 'OUT_OF_BOUNDS').length; }
  get overlapCount(): number { return this.warnings.filter(w => w.type === 'OVERLAP').length; }
}
