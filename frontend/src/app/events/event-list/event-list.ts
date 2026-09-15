// 実行環境: ブラウザ側。SC-02の一覧部分（イベント一覧）。
// 各行を展開すると詳細（API-02）を取得して表示し、その場で申込（API-03）もできる（機能追加）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { EventApiService, EventDetail, EventSummary } from '../../core/event-api';
import { ApplicationApiService } from '../../core/application-api';
import { DummyUserStore } from '../../core/dummy-user-store';

@Component({
  selector: 'app-event-list',
  imports: [CommonModule, RouterLink],
  templateUrl: './event-list.html',
  styleUrl: './event-list.css',
})
export class EventList implements OnInit {
  protected readonly events = signal<EventSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly expandedEventId = signal<number | null>(null);
  protected readonly expandedDetail = signal<EventDetail | null>(null);
  protected readonly expandedLoading = signal(false);
  protected readonly expandedError = signal<string | null>(null);

  protected readonly applying = signal(false);
  protected readonly applyErrorMessage = signal<string | null>(null);

  constructor(
    private readonly eventApi: EventApiService,
    private readonly applicationApi: ApplicationApiService,
    private readonly router: Router,
    protected readonly dummyUserStore: DummyUserStore,
  ) {}

  // 要件定義書E7: 管理者は申込できない（イベント詳細画面と同じ制御）
  protected get isAdmin(): boolean {
    return this.dummyUserStore.currentUserId() === '2';
  }

  ngOnInit(): void {
    this.eventApi.list().subscribe({
      next: (events) => {
        this.events.set(events);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? 'イベント一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }

  // 行の「▼／▲」を押した時：もう一度押すと閉じる。開く時は詳細APIを呼んで取得する
  protected toggleExpand(eventId: number): void {
    if (this.expandedEventId() === eventId) {
      this.expandedEventId.set(null);
      return;
    }

    this.expandedEventId.set(eventId);
    this.expandedDetail.set(null);
    this.expandedError.set(null);
    this.applyErrorMessage.set(null);
    this.expandedLoading.set(true);

    this.eventApi.detail(eventId).subscribe({
      next: (detail) => {
        this.expandedDetail.set(detail);
        this.expandedLoading.set(false);
      },
      error: (err) => {
        this.expandedError.set(err.error?.message ?? 'イベント詳細の取得に失敗しました。');
        this.expandedLoading.set(false);
      },
    });
  }

  // 展開部分の「申し込む」。API-03を呼び、成功したら申込完了画面へ画面遷移する
  protected apply(eventId: number): void {
    this.applyErrorMessage.set(null);
    this.applying.set(true);

    this.applicationApi.apply(eventId).subscribe({
      next: (application) => {
        this.applying.set(false);
        this.router.navigate(['/events', eventId, 'done'], { state: { application } });
      },
      error: (err) => {
        this.applying.set(false);
        this.applyErrorMessage.set(err.error?.message ?? '申込に失敗しました。');
      },
    });
  }
}
