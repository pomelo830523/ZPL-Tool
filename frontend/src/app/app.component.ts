import { Component, OnInit } from '@angular/core';
import { NgIf, NgFor } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface RenderWarning {
  type: 'OUT_OF_BOUNDS' | 'OVERLAP' | 'BARCODE_GAP' | 'SYNTAX';
  fieldA: string;
  fieldB?: string;
  detail: string;
  command?: string;
  line?: number;
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
  imports: [FormsModule, NgIf, NgFor],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {

  // ── Tab state ───────────────────────────────────────────────────────
  activeTab: 'zpl' | 'png' = 'zpl';

  // ════════════════════════════════════════════════════════════════════
  //  ZPL → PNG tab
  // ════════════════════════════════════════════════════════════════════

  // 152 × 102 mm label (1216 × 816 dots at 8 dpmm) — showcases all supported commands
  zplInput = `~DGR:ZPLV0001.GRF,1806,14,00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008000000000000000000000000001E000000000000000000000000001E000000000000000000000000001600000000000000000000000000120000000000000000000000000012000000000000000000000000001400000000000000000000000000140000000000000000000000000014000000000000000000000000001400000000000000000000003FF8140000000000000000000000FDBF140000000000000000000007C0C1A4000000000000000000000E6060E40000000000000000000038302044000000000000000000006010308C00000000000000000000C00FFF8F80000000000000000000C07E608FFF00000000000000000081E4609FC7E00000000000000000870460A4703C00000000000000009C0443E4180F0000000000000000B008DFFE0E01C000000000000000C008F0078700E000000000000000C008E001C5803800000000000000401980003CC00C00000000000000601B00001C6006000000000000003C1E00000E301F000000000000000F9800000719FF8000000000000003F00000039F80C000000000000000C0000000D60060000000000000038000000076002000000000000006000000003B00300000000000001C0000000019801000000000000038000000000E80180000000000006000001000035FF8000000000000C00000000001F83C000000000003000000000000E004000000000006000000000000700400000000000C000000000000300600000000001808000000000018060000000000300000000000000C0600000000006000000000000006020000000000C000000000000003020000000001800000000000000102000000000300000000000000008200000000060000000000000000C2000000000C00000000000000006200000000080000000000000000220000000018000000000000000032000000003000000000000000001A000000006000000000000000000E000000004000000000000800000600000000C000080000000000000600000000C0000000400000002003000000018000000000000000000100000001000000000000000000018000000300000000000000000000C000000600000000000000000000C00000060000000000000000000060000006000000000000000000006000000C000000000000000000002000000C00000000000000000000300000080080000000000000000010000018000000000000008000001800001800000040000000000000180000100000000000080000001008000030000000000000000000000C000030000000000000000000000C000030000000000000000000000400003000000000000000000000040000200000000000000000000006000020000000000000000000000600006000000000000000000000060000600000000000000000000002000060000000000000000000000200006000000000000000000000020000600000000000800002000002000060000000000000000000000300004000000000000000000000030000600000000000000000000203000060040000000000000000000200006000000000000000000000020000600000000000000000000002000060000000200000000000000200006000000000000000000000060000300000000000000000000006000030000000000000000000000400003000000000000000000000040000100000000000000080000004000010000000000000000000000C00001800000000000000000000080000080000000000000000000018000008000000000100000000001800000C000000000000000000001000000C000000000000000000003000000601000000000000000000200000060000000000000000000060000002000000000000000000004000000300000000000000000000C000000100000000000000000001800000018000000000000000080100000000C000000000000000000300000000C0000000000000000006000000006000000000000000000E000000003000000000000000001C0000000018000000010000000038000000000C000000000000000030000000000E00000000000100006000000000030000800000000000C000000000018000000000000001800000000000E000000000000007000000000000700000000000000E0000000000001C0000000000001C000000000000070000000000007000000000000001C00000000001C00000000000000038000000000F00000000000000000F000000003C000000000000000003E0000001F00000000000000000003F00007F8000000000000000000007FFFFF800000000000000000000000FFC0000000000000000000000000000000000000000
^XA
^FO400,145^XGR:ZPLV0001.GRF,1,1^FS
^XZ

^XA
^PW1219
^LL813
^LH0,0

^FO30,25^A0N,45,38^FDZPL Tool^FS
^FO30,65^GB1150,3,3^FS

^FO30,80^A0N,22,18^FD - Fonts - ^FS
^FO30,110^A0N,30,24^FD(A0N)ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-0123456789^FS

^FO30,140^GB1150,2,2^FS

^FO30,150^A0N,22,18^FD - Graphics - ^FS
^FO30,180^GB80,80,80^FS
^FO50,275^A0N,14,12^FDfilled^FS

^FO140,180^GB80,80,4^FS
^FO160,275^A0N,14,12^FDhollow^FS

^FO260,250^GB100,4,4^FS
^FO300,275^A0N,14,12^FDline^FS

^FO440,275^A0N,14,12^FDLogo^FS

^FO30,300^GB1150,2,2^FS

^FO30,312^A0N,22,18^FD - Barcodes - ^FS
^FO30,340^BY2
^BCN,80,N,N,N^FDHELLO128^FS
^FO120,430^A0N,14,12^FDCode-128^FS

^FO320,340^BY2
^B3N,80,Y,N^FDHELLO39^FS
^FO410,430^A0N,14,12^FDCode-39^FS

^FO580,355
^BQN,2,4
^FDLA,https://example.com^FS
^FO600,430^A0N,14,12^FDQR Code^FS

^FO680,330
^BXN,100,100
^FD Data Matrix https://example.com Data Matrix https://example.com Data Matrix https://example.com Data Matrix https://example.com^FS
^FO700,430^A0N,14,12^FDDataMatrix^FS

^FO30,460^GB1150,2,2^FS

^FO30,480^A0N,22,18^FD - Check Overlaps - ^FS
^FO50,530^A0N,34,28^FDAAA^FS
^FO90,530^A0N,34,28^FDBBB^FS

^FO220,490^BY2
^B3N,80,Y,N^FDAAA^FS
^FO320,490^BY2
^B3N,80,Y,N^FDBBB^FS

^FO500,530^A0N,34,28^FDAAA^FS
^FO540,490^BY2
^B3N,80,Y,N^FDBBB^FS

^FO770,505
^BQN,2,4
^FDLA,https://example.com^FS
^FO740,530^A0N,34,28^FDAAA^FS

^FO940,480
^BXN,100,100
^FD Data Matrix https://example.com Data Matrix https://example.com Data Matrix https://example.com Data Matrix https://example.com^FS
^FO910,550^GB150,4,4^FS

^FO30,600^GB1150,2,2^FS

^FO30,610^A0N,22,18^FD - Check Out Of Bounds - ^FS
^FO0,635^A0N,40,33^FDOOB!^FS

^FO1150,602
^BQN,2,4
^FDLA,https://example.com^FS

^FO30,675^GB1150,2,2^FS

^FO30,700^A0N,22,18^FD - Check Barcode Gap - ^FS
^FO300,700^BY2
^B3N,80,Y,N^FDHELLO39^FS
^FO560,700^BY2
^B3N,80,Y,N^FDHELLO39^FS

^FO840,700
^BQN,2,4
^FDLA,https://example.com^FS
^FO930,685
^BXN,100,100
^FD Data Matrix https://example.com Data Matrix https://example.com Data Matrix https://example.com Data Matrix https://example.com^FS

^XZ`;

  imageBase64   = '';
  isLoading     = false;
  errorMessage  = '';
  warnings: RenderWarning[] = [];
  highlightedZpl = '';

  labelWidth  = 152;
  labelHeight = 102;
  dpmm        = 8;
  barcodeHeight        = 50;
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

  ngOnInit(): void {
    this.highlightedZpl = this.computeHighlight(this.zplInput);
  }

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
      defaultBarcodeHeight: this.barcodeHeight
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

  onZplInput(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.highlightedZpl = this.computeHighlight(value);
  }

  onEditorScroll(event: Event): void {
    const ta  = event.target as HTMLTextAreaElement;
    const pre = ta.previousElementSibling as HTMLPreElement;
    pre.scrollTop  = ta.scrollTop;
    pre.scrollLeft = ta.scrollLeft;
  }

  clearAll(): void {
    this.zplInput       = '';
    this.highlightedZpl = '';
    this.imageBase64    = '';
    this.errorMessage   = '';
    this.warnings       = [];
    this.debugMode      = false;
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
  get barcodeGapCount(): number { return this.warnings.filter(w => w.type === 'BARCODE_GAP').length; }
  get syntaxCount(): number { return this.warnings.filter(w => w.type === 'SYNTAX').length; }

  // ── ZPL Syntax Highlighter ──────────────────────────────────────────

  private computeHighlight(raw: string): string {
    const esc = (s: string) => s
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    const out: string[] = [];
    let i = 0;

    while (i < raw.length) {
      const c = raw[i];

      if (c === '~') {
        i++;
        const cmd = raw.slice(i, i + 2).toUpperCase();
        out.push(`<span class="hl-tilde">~${esc(cmd)}</span>`);
        i += 2;
        // read params / data until next command
        const ps = i;
        while (i < raw.length && raw[i] !== '^' && raw[i] !== '~') i++;
        if (i > ps) out.push(`<span class="hl-dg">${esc(raw.slice(ps, i))}</span>`);

      } else if (c === '^') {
        i++;
        const cmdRaw = raw.slice(i, Math.min(i + 2, raw.length));
        const cmd    = cmdRaw.toUpperCase();
        i += cmdRaw.length;

        if (cmd === 'FD') {
          // find ^FS
          let fsIdx = -1;
          for (let j = i; j + 2 < raw.length; j++) {
            if (raw[j] === '^' && raw.slice(j + 1, j + 3).toUpperCase() === 'FS') { fsIdx = j; break; }
          }
          if (fsIdx >= 0) {
            out.push(`<span class="hl-fd">^FD</span>`);
            out.push(`<span class="hl-str">${esc(raw.slice(i, fsIdx))}</span>`);
            out.push(`<span class="hl-fd">^FS</span>`);
            i = fsIdx + 3;
          } else {
            out.push(`<span class="hl-fd">^FD</span>`);
            out.push(`<span class="hl-str">${esc(raw.slice(i))}</span>`);
            i = raw.length;
          }
        } else {
          const isLabel = cmd === 'XA' || cmd === 'XZ';
          out.push(`<span class="${isLabel ? 'hl-label' : 'hl-cmd'}">^${esc(cmdRaw)}</span>`);
          const ps = i;
          while (i < raw.length && raw[i] !== '^' && raw[i] !== '~') i++;
          if (i > ps) {
            const colored = esc(raw.slice(ps, i))
              .replace(/(\d+)/g, '<span class="hl-num">$1</span>');
            out.push(`<span class="hl-param">${colored}</span>`);
          }
        }

      } else {
        const ps = i;
        while (i < raw.length && raw[i] !== '^' && raw[i] !== '~') i++;
        out.push(esc(raw.slice(ps, i)));
      }
    }

    return out.join('');
  }

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
    this.zplInput       = this.generatedZpl;
    this.highlightedZpl = this.computeHighlight(this.zplInput);
    this.activeTab      = 'zpl';
  }
}
