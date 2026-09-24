// 実行環境: ブラウザ側。SC-02の一覧部分（イベント一覧）。
// 各行を展開すると詳細（API-02）を取得して表示し、その場で申込（API-03）もできる（機能追加）。
// 機能追加: 一覧表示／開催カレンダー表示の切替。
import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { EventApiService, EventDetail, EventSummary } from '../../core/event-api';
import { ApplicationApiService } from '../../core/application-api';
import { FavoriteStore } from '../../core/favorite-store';
import { DummyUserStore } from '../../core/dummy-user-store';

// カレンダー1マス分（当月外の日も前後の穴埋めとして含む）
interface CalendarDay {
  date: Date;
  inMonth: boolean;
  isToday: boolean;
  events: EventSummary[];
}

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

  // 機能追加（定員区分・アンケート）: 展開中カードの申込フォーム入力状態
  protected readonly selectedTicketTypeId = signal<number | null>(null);
  protected readonly extraAnswer = signal('');

  // 機能追加（開催カレンダー表示）: 表示モードの切替と、表示中の月
  protected readonly viewMode = signal<'list' | 'calendar'>('list');
  protected readonly calendarMonth = signal(this.startOfMonth(new Date()));

  protected readonly calendarMonthLabel = computed(() => {
    const month = this.calendarMonth();
    return `${month.getFullYear()}年${month.getMonth() + 1}月`;
  });

  // 月の1日が入る週の日曜から、月の末日が入る週の土曜までを6週分並べる（常に42マス、レイアウトが安定する）
  protected readonly calendarWeeks = computed<CalendarDay[][]>(() => {
    const month = this.calendarMonth();
    const events = this.events();
    const today = new Date();
    const todayKey = this.dateKey(today);

    const eventsByDate = new Map<string, EventSummary[]>();
    for (const event of events) {
      const key = event.startAt.slice(0, 10);
      const list = eventsByDate.get(key);
      if (list) {
        list.push(event);
      } else {
        eventsByDate.set(key, [event]);
      }
    }

    const firstCell = new Date(month.getFullYear(), month.getMonth(), 1 - month.getDay());
    const days: CalendarDay[] = [];
    for (let i = 0; i < 42; i++) {
      const date = new Date(firstCell.getFullYear(), firstCell.getMonth(), firstCell.getDate() + i);
      const key = this.dateKey(date);
      days.push({
        date,
        inMonth: date.getMonth() === month.getMonth(),
        isToday: key === todayKey,
        events: eventsByDate.get(key) ?? [],
      });
    }

    const weeks: CalendarDay[][] = [];
    for (let i = 0; i < days.length; i += 7) {
      weeks.push(days.slice(i, i + 7));
    }
    return weeks;
  });

  // 機能追加（お気に入り）: 登録済みのイベントID一覧はFavoriteStore（画面間で共有）から参照する
  protected readonly favoriteBusyId = signal<number | null>(null);

  constructor(
    private readonly eventApi: EventApiService,
    private readonly applicationApi: ApplicationApiService,
    protected readonly favoriteStore: FavoriteStore,
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

    // お気に入りは一般ユーザー・管理者の両方が使える（要件定義書§4）。取得失敗は一覧表示をブロックしない
    this.favoriteStore.ensureLoaded().subscribe({ error: () => {} });
  }

  // 機能追加（お気に入り）: 登録・解除はどちらも冪等（要件定義書§8 E9）
  protected toggleFavorite(eventId: number): void {
    this.favoriteBusyId.set(eventId);
    this.favoriteStore.toggle(eventId).subscribe({
      next: () => this.favoriteBusyId.set(null),
      error: (err) => {
        this.favoriteBusyId.set(null);
        alert(err.error?.message ?? 'お気に入りの更新に失敗しました。');
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
    this.selectedTicketTypeId.set(null);
    this.extraAnswer.set('');
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

  protected setViewMode(mode: 'list' | 'calendar'): void {
    this.viewMode.set(mode);
  }

  protected previousMonth(): void {
    const month = this.calendarMonth();
    this.calendarMonth.set(new Date(month.getFullYear(), month.getMonth() - 1, 1));
  }

  protected nextMonth(): void {
    const month = this.calendarMonth();
    this.calendarMonth.set(new Date(month.getFullYear(), month.getMonth() + 1, 1));
  }

  private startOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }

  private dateKey(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  protected onTicketTypeChange(value: string): void {
    this.selectedTicketTypeId.set(value ? Number(value) : null);
  }

  protected onExtraAnswerInput(value: string): void {
    this.extraAnswer.set(value);
  }

  // 展開部分の「申し込む」。API-03を呼び、成功したら申込完了画面へ画面遷移する
  protected apply(eventId: number): void {
    this.applyErrorMessage.set(null);
    this.applying.set(true);

    const ticketTypeId = this.selectedTicketTypeId() ?? undefined;
    const extraAnswer = this.extraAnswer().trim() || undefined;

    this.applicationApi.apply(eventId, ticketTypeId, extraAnswer).subscribe({
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
