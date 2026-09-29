// 実行環境: ブラウザ側。イベント検索画面（機能追加）。
// GET /api/eventsで全件取得し、キーワード（イベント名・場所）・開催日時の範囲でAngular側のみで絞り込む
// （バックエンドAPIは変更しない。想定件数が数十件程度のため実用上問題ない）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventApiService, EventSummary } from '../../core/event-api';

@Component({
  selector: 'app-event-search',
  imports: [CommonModule, RouterLink],
  templateUrl: './event-search.html',
  styleUrl: './event-search.css',
})
export class EventSearch implements OnInit {
  protected readonly events = signal<EventSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly keyword = signal('');
  protected readonly dateFrom = signal('');
  protected readonly dateTo = signal('');

  // D-14（機能追加）: 並び替え。APIの再取得は行わず、絞り込み結果を画面側で並び替える
  protected readonly sortOrder = signal<'startAt' | 'accepted_desc' | 'favorite_desc' | 'deadline_asc'>('startAt');

  protected readonly filteredEvents = computed(() => {
    const keyword = this.keyword().trim().toLowerCase();
    const from = this.dateFrom();
    const to = this.dateTo();

    const filtered = this.events().filter((event) => {
      if (keyword && !event.name.toLowerCase().includes(keyword) && !event.place.toLowerCase().includes(keyword)) {
        return false;
      }
      const startDate = event.startAt.slice(0, 10);
      if (from && startDate < from) {
        return false;
      }
      if (to && startDate > to) {
        return false;
      }
      return true;
    });

    switch (this.sortOrder()) {
      case 'accepted_desc':
        return filtered.sort((a, b) => b.acceptedCount - a.acceptedCount);
      case 'favorite_desc':
        return filtered.sort((a, b) => b.favoriteCount - a.favoriteCount);
      case 'deadline_asc':
        return filtered.sort((a, b) => a.applicationDeadline.localeCompare(b.applicationDeadline));
      default:
        return filtered.sort((a, b) => a.startAt.localeCompare(b.startAt));
    }
  });

  constructor(private readonly eventApi: EventApiService) {}

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

  protected onKeywordInput(value: string): void {
    this.keyword.set(value);
  }

  protected onDateFromInput(value: string): void {
    this.dateFrom.set(value);
  }

  protected onDateToInput(value: string): void {
    this.dateTo.set(value);
  }

  protected onSortOrderChange(value: string): void {
    this.sortOrder.set(value as 'startAt' | 'accepted_desc' | 'favorite_desc' | 'deadline_asc');
  }

  protected clearFilters(): void {
    this.keyword.set('');
    this.dateFrom.set('');
    this.dateTo.set('');
  }
}
