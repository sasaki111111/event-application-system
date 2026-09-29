// 実行環境: ブラウザ側。SC-04のイベント管理一覧（D-2対応、E-5）。
// GET /api/eventsはSC-02と同じAPIを流用する（API設計書の備考の通り）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { EventApiService, EventSummary } from '../../core/event-api';

@Component({
  selector: 'app-admin-event-list',
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-event-list.html',
  styleUrl: './admin-event-list.css',
})
export class AdminEventList implements OnInit {
  protected readonly events = signal<EventSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly duplicatingId = signal<number | null>(null);

  constructor(
    private readonly eventApi: EventApiService,
    private readonly router: Router,
  ) {}

  // D-16: 複製元イベントの詳細（AP-05）を取得し、新規登録フォームへ値を持って遷移する。
  // バックエンドAPIは呼ばない（開催日時・申込締切は複製対象外のため、新規登録と同じ入力チェックを通す）
  protected duplicate(event: EventSummary): void {
    this.duplicatingId.set(event.id);
    this.eventApi.detail(event.id).subscribe({
      next: (detail) => {
        this.duplicatingId.set(null);
        this.router.navigate(['/admin/events/new'], { state: { duplicateFrom: detail } });
      },
      error: (err) => {
        this.duplicatingId.set(null);
        alert(err.error?.message ?? '複製に失敗しました。');
      },
    });
  }

  ngOnInit(): void {
    this.loadEvents();
  }

  protected deleteEvent(event: EventSummary): void {
    if (!confirm(`「${event.name}」を削除しますか？（削除済みイベント画面から後で復元できます）`)) {
      return;
    }
    this.eventApi.remove(event.id).subscribe({
      next: () => this.loadEvents(),
      error: (err) => {
        // 受付済の申込がある場合は400（業務エラー）、権限が無ければ403が返る（API設計書 API-08）
        const message = err.error?.message ?? '削除に失敗しました。';
        alert(message);
      },
    });
  }

  private loadEvents(): void {
    this.loading.set(true);
    this.eventApi.list('all').subscribe({
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
}
