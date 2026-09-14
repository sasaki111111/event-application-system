// 実行環境: ブラウザ側。SC-03のマイページ（D-4: 一覧表示、D-5: キャンセル操作）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApplicationApiService, MyApplication } from '../core/application-api';

@Component({
  selector: 'app-my-applications',
  imports: [CommonModule, RouterLink],
  templateUrl: './my-applications.html',
  styleUrl: './my-applications.css',
})
export class MyApplications implements OnInit {
  protected readonly applications = signal<MyApplication[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly cancellingId = signal<number | null>(null);

  constructor(private readonly applicationApi: ApplicationApiService) {}

  ngOnInit(): void {
    this.loadApplications();
  }

  // API-05: すでにキャンセル済／開催日時経過は400（要件定義書§8「取消可否チェック」）
  protected cancel(application: MyApplication): void {
    if (!confirm(`「${application.eventName}」への申込を取り消しますか？`)) {
      return;
    }

    this.cancellingId.set(application.id);
    this.applicationApi.cancel(application.id).subscribe({
      next: () => this.loadApplications(),
      error: (err) => {
        this.cancellingId.set(null);
        alert(err.error?.message ?? '取消に失敗しました。');
      },
    });
  }

  private loadApplications(): void {
    this.loading.set(true);
    this.cancellingId.set(null);
    this.applicationApi.myApplications().subscribe({
      next: (applications) => {
        this.applications.set(applications);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? '申込一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
