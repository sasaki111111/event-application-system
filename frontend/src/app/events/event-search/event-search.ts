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
  // 機能追加: カテゴリ絞り込み（フロント側のみ、選択肢は取得済みイベントのカテゴリ値から生成）
  protected readonly category = signal('');

  protected readonly categoryOptions = computed(() => {
    const categories = this.events()
      .map((event) => event.category)
      .filter((category): category is string => !!category);
    return Array.from(new Set(categories)).sort();
  });

  protected readonly filteredEvents = computed(() => {
    const keyword = this.keyword().trim().toLowerCase();
    const from = this.dateFrom();
    const to = this.dateTo();
    const category = this.category();

    return this.events().filter((event) => {
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
      if (category && event.category !== category) {
        return false;
      }
      return true;
    });
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

  protected onCategoryInput(value: string): void {
    this.category.set(value);
  }

  protected clearFilters(): void {
    this.keyword.set('');
    this.dateFrom.set('');
    this.dateTo.set('');
    this.category.set('');
  }
}
