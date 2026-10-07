// 実行環境: ブラウザ側。SC-023の申込状況・実績画面（E-6）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { EventReport, ReportApiService, ReportSort } from '../../core/report-api';

@Component({
  selector: 'app-admin-report',
  imports: [CommonModule],
  templateUrl: './admin-report.html',
  styleUrl: './admin-report.css',
})
/**
 * SC-023 申込状況・実績画面（/admin/reports）を担当するComponent。
 * イベントごとの申込実績（定員・受付済数・充足率）を一覧表示し、並び替えとCSVダウンロードができる。
 *
 * - ルーティング定義（app.routes.ts）でこのパスには authGuard・adminGuard が設定されており、
 *   管理者以外のユーザーはアクセスできない（＝管理者専用画面）。
 * - 利用するAngular Service: ReportApiService（実績集計の取得、CSV形式でのダウンロード）。
 * - 画面遷移: この画面自体には他画面への遷移リンクは無い。
 */
export class AdminReport implements OnInit {
  protected readonly reports = signal<EventReport[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  // 一覧の並び順（'startAt'=開催日時順、'accepted_desc'=申込数の多い順）。テンプレートのselectと連動する
  protected readonly sort = signal<ReportSort>('startAt');
  protected readonly downloading = signal(false);

  constructor(private readonly reportApi: ReportApiService) {}

  /**
   * Angularのライフサイクルフックの一つ。Componentが画面に表示される直前に一度だけ実行される。
   * ここでは実績一覧の初回読み込みを行う。
   */
  ngOnInit(): void {
    this.load();
  }

  /**
   * 並び順セレクトボックス（(change)="changeSort(...)"）から呼ばれる処理。
   * @param sort 選択された並び順（'startAt' または 'accepted_desc'）
   */
  protected changeSort(sort: ReportSort): void {
    // 選択された並び順をsignalに反映し、その順序で一覧を再取得する
    this.sort.set(sort);
    this.load();
  }

  /**
   * 「CSVダウンロード」ボタン（(click)="downloadCsv()"）から呼ばれる処理。
   * API-09 format=csv。X-User-Idヘッダが必要なため、Blobとして取得してからダウンロードさせる。
   * URL.createObjectURL()でBlob（バイナリデータ）を一時的なURLに変換し、<a>タグのクリックを
   * JavaScriptから実行することでファイルダウンロードを発生させている。
   */
  protected downloadCsv(): void {
    // ダウンロード中表示に切り替える
    this.downloading.set(true);
    // CSV形式のレポートをBlob（バイナリデータ）として取得する
    this.reportApi.downloadCsv().subscribe({
      next: (blob) => {
        this.downloading.set(false);
        // Blobを一時的なURLに変換する
        const url = URL.createObjectURL(blob);
        // 画面には表示しない<a>要素を動的に作り、ダウンロード用のURL・ファイル名を設定する
        const link = document.createElement('a');
        link.href = url;
        link.download = 'applications_report.csv';
        // クリックイベントをコードから発生させ、ファイルダウンロードを実行する
        link.click();
        // 一時的なURLは使い終わったら解放する（メモリリーク防止）
        URL.revokeObjectURL(url);
      },
      error: () => {
        // 失敗したらダウンロード中状態を解除し、アラートで知らせる
        this.downloading.set(false);
        alert('CSVのダウンロードに失敗しました。');
      },
    });
  }

  /** 現在の並び順（this.sort()）で申込実績一覧をAPIから取得し直す。 */
  private load(): void {
    // 読み込み中表示に切り替える
    this.loading.set(true);
    // 現在選択中の並び順で実績一覧を取得する
    this.reportApi.summary(this.sort()).subscribe({
      next: (reports) => {
        // 取得した一覧をsignalに反映し、読み込み中表示を終える
        this.reports.set(reports);
        this.loading.set(false);
      },
      error: (err) => {
        // 取得失敗時はエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? '申込実績の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
