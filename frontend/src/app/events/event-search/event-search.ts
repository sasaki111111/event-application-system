// 実行環境: ブラウザ側。イベント検索画面（機能追加）。
// GET /api/eventsで全件取得し、キーワード（イベント名・場所）・開催日時の範囲でAngular側のみで絞り込む
// （バックエンドAPIは変更しない。想定件数が数十件程度のため実用上問題ない）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventApiService, EventSummary } from '../../core/event-api';
import { moveHeldEventsLast } from '../../core/event-order';

/**
 * イベント検索画面（機能追加、/events/search）を担当するComponent。
 * イベント一覧を一度だけ全件取得し、キーワード・開催日の範囲・並び替えは
 * すべてブラウザ側（computed）で絞り込む。APIへの再問い合わせは行わない。
 *
 * 使用するAngular Service:
 * - `EventApiService`: イベント一覧取得API（API-01）の呼び出し。
 *
 * 画面遷移: 検索結果のカードのタイトルからevent-detail.ts（イベント詳細）へ遷移する
 * （?from=searchを付けて渡すため、詳細画面の「戻る」でこの検索画面に戻れる）。
 */
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

  // （機能追加）: 並び替え。APIの再取得は行わず、絞り込み結果を画面側で並び替える
  protected readonly sortOrder = signal<'startAt' | 'accepted_desc' | 'favorite_desc' | 'deadline_asc'>('startAt');

  /**
   * `computed()`は、他のsignal（ここではevents/keyword/dateFrom/dateTo/sortOrder）の値から
   * 自動的に導き出される「計算結果のsignal」を作るAngularの機能。元になるsignalのどれかが
   * 変わると、この値も自動的に再計算される。ここではキーワード・期間での絞り込みと並び替えをまとめて行う。
   */
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
        filtered.sort((a, b) => b.acceptedCount - a.acceptedCount);
        break;
      case 'favorite_desc':
        filtered.sort((a, b) => b.favoriteCount - a.favoriteCount);
        break;
      case 'deadline_asc':
        filtered.sort((a, b) => a.applicationDeadline.localeCompare(b.applicationDeadline));
        break;
      default:
        filtered.sort((a, b) => a.startAt.localeCompare(b.startAt));
    }
    // イベント一覧（SC-020）と同じく、どの並び順でも開催済みのイベントは末尾にまとめて表示する
    return moveHeldEventsLast(filtered);
  });

  constructor(private readonly eventApi: EventApiService) {}

  /**
   * ngOnInitは、Angularのライフサイクルフックの一つ。Componentが画面に表示される
   * 直前に一度だけ自動的に実行される。ここではイベント一覧を取得する（以降の絞り込み・
   * 並び替えはすべてこの取得済み一覧に対してブラウザ側で行う）。
   */
  ngOnInit(): void {
    // `.subscribe({ next, error })`は、Observable（非同期で届くデータの流れ）の結果を
    // 受け取るための書き方。成功時はnext、失敗時はerrorに渡した処理が実行される。
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

  /** キーワード入力欄の`(input)`イベントで呼ばれる。入力のたびに絞り込み結果（filteredEvents）が再計算される。 */
  protected onKeywordInput(value: string): void {
    this.keyword.set(value);
  }

  /** 「開催日（から）」入力欄の`(input)`イベントで呼ばれる。 */
  protected onDateFromInput(value: string): void {
    this.dateFrom.set(value);
  }

  /** 「開催日（まで）」入力欄の`(input)`イベントで呼ばれる。 */
  protected onDateToInput(value: string): void {
    this.dateTo.set(value);
  }

  /** 並び替え用`<select>`の`(change)`イベントで呼ばれる。 */
  protected onSortOrderChange(value: string): void {
    this.sortOrder.set(value as 'startAt' | 'accepted_desc' | 'favorite_desc' | 'deadline_asc');
  }

  /** 「条件をクリア」ボタン（(click)）で呼ばれる。キーワード・開催日の範囲の入力をすべて空に戻す。 */
  protected clearFilters(): void {
    this.keyword.set('');
    this.dateFrom.set('');
    this.dateTo.set('');
  }
}
