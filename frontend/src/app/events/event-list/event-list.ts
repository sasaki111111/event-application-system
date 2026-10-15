// 実行環境: ブラウザ側。SC-020の一覧部分（イベント一覧）。
// 各行を展開すると詳細（API-02）を取得して表示し、その場で申込（API-03）もできる（機能追加）。
// 機能追加: 一覧表示／開催カレンダー表示の切替。
// `computed()`は、他のsignalの値から自動的に導き出される「計算結果のsignal」を作るAngularの機能。
// 元になるsignal（例: events, sortOrder）が変わると、computed()の値も自動的に再計算される。
import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EventApiService, EventDetail, EventSummary } from '../../core/event-api';
import { moveHeldEventsLast } from '../../core/event-order';
import { ApplicationApiService } from '../../core/application-api';
import { FavoriteStore } from '../../core/favorite-store';
import { LoginUserStore } from '../../core/login-user-store';

// カレンダー1マス分（当月外の日も前後の穴埋めとして含む）
interface CalendarDay {
  date: Date;
  inMonth: boolean;
  isToday: boolean;
  events: EventSummary[];
}

/**
 * SC-020イベント一覧画面を担当するComponent。カード形式の一覧表示と、
 * 開催カレンダー表示（機能追加）の2つの表示モードを切り替えられる。
 * 一覧の各カードは「▼詳細を見る」で展開でき、展開したカード内でその場で申込もできる。
 *
 * 使用するAngular Service:
 * - `EventApiService`: イベント一覧取得API（API-01）とイベント詳細取得API（API-02、カード展開時）。
 * - `ApplicationApiService`: 申込API（API-03）の呼び出し。
 * - `FavoriteStore`: お気に入り登録状態を画面間で共有する状態管理。
 * - `Router`: 申込成功後に申込完了画面へ遷移するために使う。
 * - `ActivatedRoute`: カレンダー表示から戻ってきた場合の表示モード・表示月（?view=calendar&month=…）を読み取る。
 * - `LoginUserStore`: ログイン中ユーザーが管理者かどうかの判定に使う。
 *
 * 画面遷移: カードのタイトル／カレンダーの日付リンクからevent-detail.ts（イベント詳細）へ遷移する。
 * 申込成功時はapply-done.ts（申込完了画面）へ遷移する。
 */
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

  // （機能追加）: 一覧表示の並び替え。APIの再取得は行わず、取得済みの一覧を画面側で並び替える
  protected readonly sortOrder = signal<'startAt' | 'accepted_desc' | 'favorite_desc' | 'deadline_asc'>('startAt');

  /**
   * sortOrderの値に応じて、取得済みのeventsを並び替えた結果。sortOrderが変わると自動的に再計算される。
   * どの並び順でも、開催済み（開催日時が現在時刻以前）のイベントは末尾にまとめて表示する
   * （これから開催されるイベントを先に見せるため。docs/30_詳細設計/30_画面詳細設計書.md SC-020）。
   * 開催前・開催済みそれぞれの中では、選択された並び順に従う。
   */
  protected readonly sortedEvents = computed(() => {
    const events = [...this.events()];
    switch (this.sortOrder()) {
      case 'accepted_desc':
        events.sort((a, b) => b.acceptedCount - a.acceptedCount);
        break;
      case 'favorite_desc':
        events.sort((a, b) => b.favoriteCount - a.favoriteCount);
        break;
      case 'deadline_asc':
        events.sort((a, b) => a.applicationDeadline.localeCompare(b.applicationDeadline));
        break;
      default:
        events.sort((a, b) => a.startAt.localeCompare(b.startAt));
    }
    // 並び替えた順序を保ったまま、開催前のイベントを前、開催済みのイベントを後ろに分ける（SC-021と共通の処理）
    return moveHeldEventsLast(events);
  });

  /** カレンダー見出しに表示する「2027年3月」のような文字列。calendarMonthから導き出す。 */
  protected readonly calendarMonthLabel = computed(() => {
    const month = this.calendarMonth();
    return `${month.getFullYear()}年${month.getMonth() + 1}月`;
  });

  // 機能追加（カレンダー表示からの詳細遷移）: 詳細画面の「戻る」がこの表示モード・月に戻れるよう、遷移先へ引き継ぐ値
  protected readonly calendarMonthParam = computed(() => {
    const month = this.calendarMonth();
    return `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, '0')}`;
  });

  // 月の1日が入る週の日曜から、月の末日が入る週の土曜までを6週分並べる（常に42マス、レイアウトが安定する）
  /** カレンダー表示用に、1マス＝1日分のデータ（CalendarDay）を週単位の2次元配列に組み立てた結果。 */
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

  // コンストラクタの引数に型を書くと、Angularが対応するServiceのインスタンスを自動的に渡してくれる
  // （依存性注入／DI）。login.tsの`inject()`と役割は同じで、こちらはAngularの元からある書き方。
  constructor(
    private readonly eventApi: EventApiService,
    private readonly applicationApi: ApplicationApiService,
    protected readonly favoriteStore: FavoriteStore,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    protected readonly loginUserStore: LoginUserStore,
  ) {}

  // 要件定義書E7: 管理者は申込できない（イベント詳細画面と同じ制御）。role基準で判定する
  protected get isAdmin(): boolean {
    return this.loginUserStore.isAdmin();
  }

  /**
   * ngOnInitは、Angularのライフサイクルフックの一つ。Componentが画面に表示される
   * 直前に一度だけ自動的に実行される。ここでは、カレンダー表示から戻ってきた場合の
   * 表示モード・表示月の復元と、イベント一覧・お気に入り状態の取得を行っている。
   */
  ngOnInit(): void {
    // 機能追加（カレンダー表示からの詳細遷移）: イベント詳細の「戻る」がこの画面へ渡すview/month
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.get('view') === 'calendar') {
      this.viewMode.set('calendar');
      const month = queryParams.get('month');
      const parsed = month?.match(/^(\d{4})-(\d{2})$/);
      if (parsed) {
        this.calendarMonth.set(new Date(Number(parsed[1]), Number(parsed[2]) - 1, 1));
      }
    }

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

    // お気に入りは一般ユーザー・管理者の両方が使える（要件定義書§4）。取得失敗は一覧表示をブロックしない
    this.favoriteStore.ensureLoaded().subscribe({ error: () => {} });
  }

  // 機能追加（お気に入り）: 登録・解除はどちらも冪等（要件定義書§8 E9）
  /** カードの「☆/★」ボタン（(click)）で呼ばれる。対象イベントのお気に入りを登録・解除する。 */
  protected toggleFavorite(eventId: number): void {
    this.favoriteBusyId.set(eventId);
    this.favoriteStore.toggle(eventId).subscribe({
      next: (favorited) => {
        this.favoriteBusyId.set(null);
        // カードの「お気に入り数」も即座に反映する（一覧を取り直さず、表示中の件数を1増減する）
        this.events.update((events) =>
          events.map((event) =>
            event.id === eventId
              ? { ...event, favoriteCount: Math.max(0, event.favoriteCount + (favorited ? 1 : -1)) }
              : event,
          ),
        );
      },
      error: (err) => {
        this.favoriteBusyId.set(null);
        alert(err.error?.message ?? 'お気に入りの更新に失敗しました。');
      },
    });
  }

  // 行の「▼／▲」を押した時：もう一度押すと閉じる。開く時は詳細APIを呼んで取得する
  /** カードの「▼詳細を見る／▲閉じる」ボタン（(click)）で呼ばれる。 */
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

  /** 「一覧表示／カレンダー表示」切替ボタン（(click)）で呼ばれる。 */
  protected setViewMode(mode: 'list' | 'calendar'): void {
    this.viewMode.set(mode);
  }

  /** 並び替え用`<select>`の`(change)`イベントで呼ばれる。 */
  protected onSortOrderChange(value: string): void {
    this.sortOrder.set(value as 'startAt' | 'accepted_desc' | 'favorite_desc' | 'deadline_asc');
  }

  /** カレンダーの「← 前月」ボタン（(click)）で呼ばれる。表示中の月を1つ前に戻す。 */
  protected previousMonth(): void {
    const month = this.calendarMonth();
    this.calendarMonth.set(new Date(month.getFullYear(), month.getMonth() - 1, 1));
  }

  /** カレンダーの「翌月 →」ボタン（(click)）で呼ばれる。表示中の月を1つ先に進める。 */
  protected nextMonth(): void {
    const month = this.calendarMonth();
    this.calendarMonth.set(new Date(month.getFullYear(), month.getMonth() + 1, 1));
  }

  /** 指定した日付が属する月の1日（時刻は0時）を返す補助関数。 */
  private startOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }

  /** 日付を"YYYY-MM-DD"形式の文字列に変換する補助関数。イベントの日付と突き合わせるためのキーとして使う。 */
  private dateKey(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /** 展開カード内の参加区分`<select>`の`(change)`イベントで呼ばれる。 */
  protected onTicketTypeChange(value: string): void {
    this.selectedTicketTypeId.set(value ? Number(value) : null);
  }

  /** 展開カード内のアンケート`<textarea>`の`(input)`イベントで呼ばれる。 */
  protected onExtraAnswerInput(value: string): void {
    this.extraAnswer.set(value);
  }

  // 展開部分の「申し込む」。API-03を呼び、成功したら申込完了画面へ画面遷移する
  /** 展開カード内の「申し込む」ボタン（(click)）で呼ばれる。 */
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
