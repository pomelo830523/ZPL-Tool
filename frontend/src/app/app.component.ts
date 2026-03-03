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

interface PngToZplResponse {
  zpl: string;
  previewImage: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {

  // ── Tab state ───────────────────────────────────────────────────────
  activeTab: 'zpl' | 'png' = 'zpl';

  // ════════════════════════════════════════════════════════════════════
  //  ZPL → PNG tab
  // ════════════════════════════════════════════════════════════════════

  zplInput = `^XA
^FO50,40^A0N,60,50^FDHello, ZPL World!^FS
^FO50,130^A0N,30,25^FDLocal rendering - no cloud needed^FS
^FO50,200^BY2^BCN,100,Y,N,N^FD123456789012^FS
^FO500,200^BQN,2,5^FDhttps://example.com^FS
^FO50,380^GB700,4,4^FS
^FO50,410^A0N,25,20^FDCode 39 barcode:^FS
^FO50,445^BY1^B3N,80,Y,N^FDHELLO-WORLD^FS
^XZ`;

  imageBase64   = '';
  isLoading     = false;
  errorMessage  = '';
  warnings: RenderWarning[] = [];

  labelWidth  = 4;
  labelHeight = 6;
  dpmm        = 8;
  overlapThresholdDots = 5;
  debugMode = false;

  private readonly zplApiUrl = 'http://localhost:8080/api/zpl/convert';

  // ════════════════════════════════════════════════════════════════════
  //  PNG → ZPL tab
  // ════════════════════════════════════════════════════════════════════

  pngInputBase64  = '';   // raw base64 of uploaded PNG (for display)
  pngIsLoading    = false;
  pngError        = '';
  generatedZpl    = '';
  pngOverlayBase64 = '';  // detection overlay image

  pngThreshold    = 128;
  pngMinShapeDots = 20;
  pngTessDataPath = '';

  private readonly pngApiUrl = 'http://localhost:8080/api/png/to-zpl';

  constructor(private http: HttpClient) {}

  // ────────────────────────────────────────────────────────────────────
  //  ZPL → PNG methods
  // ────────────────────────────────────────────────────────────────────

  convertZpl(): void {
    if (!this.zplInput.trim()) {
      this.errorMessage = '請輸入 ZPL 代碼';
      return;
    }

    this.isLoading    = true;
    this.errorMessage = '';
    this.imageBase64  = '';
    this.warnings     = [];

    this.http.post<ConvertResponse>(this.zplApiUrl, {
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

  // ────────────────────────────────────────────────────────────────────
  //  PNG → ZPL methods
  // ────────────────────────────────────────────────────────────────────

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      // Store full data URL for <img> preview; API accepts the base64 part
      this.pngInputBase64 = dataUrl;
      this.pngError = '';
      this.generatedZpl = '';
      this.pngOverlayBase64 = '';
    };
    reader.readAsDataURL(file);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.pngInputBase64 = reader.result as string;
      this.pngError = '';
      this.generatedZpl = '';
      this.pngOverlayBase64 = '';
    };
    reader.readAsDataURL(file);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  convertPng(): void {
    if (!this.pngInputBase64) {
      this.pngError = '請先上傳 PNG 圖片';
      return;
    }

    this.pngIsLoading    = true;
    this.pngError        = '';
    this.generatedZpl    = '';
    this.pngOverlayBase64 = '';

    this.http.post<PngToZplResponse>(this.pngApiUrl, {
      image:        this.pngInputBase64,
      threshold:    this.pngThreshold,
      minShapeDots: this.pngMinShapeDots,
      tessDataPath: this.pngTessDataPath
    }).subscribe({
      next: (res) => {
        this.generatedZpl     = res.zpl;
        this.pngOverlayBase64 = res.previewImage;
        this.pngIsLoading     = false;
      },
      error: (err) => {
        this.pngError     = err.error?.error ?? '轉換失敗，請確認後端服務已啟動 (port 8080)';
        this.pngIsLoading = false;
      }
    });
  }

  copyZpl(): void {
    navigator.clipboard.writeText(this.generatedZpl).catch(() => {});
  }

  sendToViewer(): void {
    this.zplInput  = this.generatedZpl;
    this.activeTab = 'zpl';
  }
}
