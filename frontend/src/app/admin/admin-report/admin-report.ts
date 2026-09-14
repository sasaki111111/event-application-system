// 実行環境: ブラウザ側。SC-05の申込状況・実績画面（D-6対応、E-6）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { EventReport, ReportApiService, ReportSort } from '../../core/report-api';

@Component({
  selector: 'app-admin-report',
  imports: [CommonModule],
  templateUrl: './admin-report.html',
  styleUrl: './admin-report.css',
})
export class AdminReport implements OnInit {
  protected readonly reports = signal<EventReport[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly sort = signal<ReportSort>('startAt');
  protected readonly downloading = signal(false);

  constructor(private readonly reportApi: ReportApiService) {}

  ngOnInit(): void {
    this.load();
  }

  protected changeSort(sort: ReportSort): void {
    this.sort.set(sort);
    this.load();
  }

  // API-09 format=csv。X-User-Idヘッダが必要なため、Blobとして取得してからダウンロードさせる
  protected downloadCsv(): void {
    this.downloading.set(true);
    this.reportApi.downloadCsv().subscribe({
      next: (blob) => {
        this.downloading.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'applications_report.csv';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.downloading.set(false);
        alert('CSVのダウンロードに失敗しました。');
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.reportApi.summary(this.sort()).subscribe({
      next: (reports) => {
        this.reports.set(reports);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? '申込実績の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
