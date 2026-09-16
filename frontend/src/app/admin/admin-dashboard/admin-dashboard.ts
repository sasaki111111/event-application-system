// 実行環境: ブラウザ側。管理者ダッシュボード画面（/admin/dashboard、機能追加）。
// 既存のAPI（イベント一覧・ユーザー一覧・申込実績集計）だけを組み合わせて、
// 総イベント数・総ユーザー数・受付済申込数（全イベント合計）をまとめて表示する。
// 新しいAPIは追加していない。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { EventApiService } from '../../core/event-api';
import { UserApiService } from '../../core/user-api';
import { ReportApiService } from '../../core/report-api';

@Component({
  selector: 'app-admin-dashboard',
  imports: [CommonModule],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.css',
})
export class AdminDashboard implements OnInit {
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly totalEvents = signal(0);
  protected readonly totalUsers = signal(0);
  protected readonly totalAcceptedApplications = signal(0);

  constructor(
    private readonly eventApi: EventApiService,
    private readonly userApi: UserApiService,
    private readonly reportApi: ReportApiService,
  ) {}

  ngOnInit(): void {
    forkJoin({
      events: this.eventApi.list('all'),
      users: this.userApi.list(),
      reports: this.reportApi.summary('startAt'),
    }).subscribe({
      next: ({ events, users, reports }) => {
        this.totalEvents.set(events.length);
        this.totalUsers.set(users.length);
        this.totalAcceptedApplications.set(reports.reduce((sum, r) => sum + r.acceptedCount, 0));
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? 'ダッシュボードの取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
