// 実行環境: ブラウザ側。機能追加（ソフトデリート）: 削除済みイベントの確認・復元画面（/admin/events/deleted）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { DeletedEvent, EventApiService } from '../../core/event-api';

@Component({
  selector: 'app-admin-deleted-events',
  imports: [CommonModule],
  templateUrl: './admin-deleted-events.html',
  styleUrl: './admin-deleted-events.css',
})
export class AdminDeletedEvents implements OnInit {
  protected readonly events = signal<DeletedEvent[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  constructor(private readonly eventApi: EventApiService) {}

  ngOnInit(): void {
    this.loadDeletedEvents();
  }

  protected restoreEvent(event: DeletedEvent): void {
    if (!confirm(`「${event.name}」を復元しますか？`)) {
      return;
    }
    this.eventApi.restore(event.id).subscribe({
      next: () => this.loadDeletedEvents(),
      error: (err) => {
        const message = err.error?.message ?? '復元に失敗しました。';
        alert(message);
      },
    });
  }

  private loadDeletedEvents(): void {
    this.loading.set(true);
    this.eventApi.listDeleted().subscribe({
      next: (events) => {
        this.events.set(events);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? '削除済みイベント一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
