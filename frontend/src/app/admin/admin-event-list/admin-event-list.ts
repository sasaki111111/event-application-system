// 実行環境: ブラウザ側。SC-04のイベント管理一覧（D-2対応、E-5）。
// GET /api/eventsはSC-02と同じAPIを流用する（API設計書の備考の通り）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
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

  constructor(private readonly eventApi: EventApiService) {}

  ngOnInit(): void {
    this.loadEvents();
  }

  protected deleteEvent(event: EventSummary): void {
    if (!confirm(`「${event.name}」を削除しますか？`)) {
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
