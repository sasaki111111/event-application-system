// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。event-list.tsの単体テスト。
// 一覧取得・並び替え・詳細展開・申込・お気に入りトグルのロジックを検証する。見た目は対象外。
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { ApplicationApiService } from '../../core/application-api';
import { LoginUserStore } from '../../core/login-user-store';
import { EventApiService, EventDetail, EventSummary } from '../../core/event-api';
import { FavoriteStore } from '../../core/favorite-store';
import { EventList } from './event-list';

function makeEvent(overrides: Partial<EventSummary>): EventSummary {
  return {
    id: 1,
    name: 'イベント',
    startAt: '2027-01-10T10:00:00',
    place: '会議室',
    capacity: 10,
    applicationDeadline: '2027-01-05T00:00:00',
    acceptedCount: 0,
    open: true,
    organizerName: null,
    imageUrl: null,
    favoriteCount: 0,
    ...overrides,
  };
}

const events: EventSummary[] = [
  makeEvent({ id: 1, name: 'A', startAt: '2027-03-01T10:00:00', applicationDeadline: '2027-02-25T00:00:00', acceptedCount: 3, favoriteCount: 1 }),
  makeEvent({ id: 2, name: 'B', startAt: '2027-01-01T10:00:00', applicationDeadline: '2027-03-01T00:00:00', acceptedCount: 10, favoriteCount: 5 }),
  makeEvent({ id: 3, name: 'C', startAt: '2027-02-01T10:00:00', applicationDeadline: '2027-01-15T00:00:00', acceptedCount: 1, favoriteCount: 3 }),
];

describe('EventList', () => {
  let eventApi: { list: ReturnType<typeof vi.fn>; detail: ReturnType<typeof vi.fn> };
  let applicationApi: { apply: ReturnType<typeof vi.fn> };
  let favoriteStore: { favoriteEventIds: ReturnType<typeof vi.fn>; ensureLoaded: ReturnType<typeof vi.fn>; toggle: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let loginUserStore: { isAdmin: ReturnType<typeof vi.fn> };
  let activatedRoute: { snapshot: { queryParamMap: { get: ReturnType<typeof vi.fn> } } };

  function createComponent(): EventList {
    TestBed.configureTestingModule({
      providers: [
        { provide: EventApiService, useValue: eventApi },
        { provide: ApplicationApiService, useValue: applicationApi },
        { provide: FavoriteStore, useValue: favoriteStore },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: activatedRoute },
        { provide: LoginUserStore, useValue: loginUserStore },
      ],
    });
    const component = TestBed.createComponent(EventList).componentInstance;
    component.ngOnInit();
    return component;
  }

  beforeEach(() => {
    eventApi = {
      list: vi.fn(() => of(events)),
      detail: vi.fn((id: number) => of({ ...events.find((e) => e.id === id), description: '説明', remaining: 5, extraQuestion: null, ticketTypes: [] } as EventDetail)),
    };
    applicationApi = { apply: vi.fn(() => of({ id: 1, eventId: 1, ticketTypeId: null, userId: 1, statusCode: 1, statusName: '受付済', appliedAt: '2026-12-01T00:00:00' })) };
    favoriteStore = {
      favoriteEventIds: vi.fn(() => new Set<number>()),
      ensureLoaded: vi.fn(() => of(new Set<number>())),
      toggle: vi.fn(() => of(true)),
    };
    router = { navigate: vi.fn() };
    loginUserStore = { isAdmin: vi.fn(() => false) };
    activatedRoute = { snapshot: { queryParamMap: { get: vi.fn(() => null) } } };
  });

  // ngOnInit実行直後、EventApiService.list()の結果がevents signalに反映されていることを確認
  it('初期表示でイベント一覧を取得する', () => {
    const component = createComponent() as any;

    expect(component.events()).toEqual(events);
    expect(component.loading()).toBe(false);
  });

  // sortOrderの初期値（'startAt'）では、開催日時の昇順で並ぶことを確認
  it('並び替え: 既定は開催日時昇順', () => {
    const component = createComponent() as any;

    expect(component.sortedEvents().map((e: EventSummary) => e.id)).toEqual([2, 3, 1]);
  });

  // onSortOrderChange('accepted_desc')で、申込数（acceptedCount）の降順に並び替わることを確認
  it('並び替え: 申込数の多い順', () => {
    const component = createComponent() as any;

    component.onSortOrderChange('accepted_desc');

    expect(component.sortedEvents().map((e: EventSummary) => e.id)).toEqual([2, 1, 3]);
  });

  // onSortOrderChange('favorite_desc')で、お気に入り数の降順に並び替わることを確認
  it('並び替え: お気に入り数の多い順', () => {
    const component = createComponent() as any;

    component.onSortOrderChange('favorite_desc');

    expect(component.sortedEvents().map((e: EventSummary) => e.id)).toEqual([2, 3, 1]);
  });

  // onSortOrderChange('deadline_asc')で、申込締切の昇順（近い順）に並び替わることを確認
  it('並び替え: 申込締切が近い順', () => {
    const component = createComponent() as any;

    component.onSortOrderChange('deadline_asc');

    expect(component.sortedEvents().map((e: EventSummary) => e.id)).toEqual([3, 1, 2]);
  });

  // toggleExpand(id)で、該当イベントのEventApiService.detail()が呼ばれ、展開状態・詳細が設定されることを確認
  // 開催済み（開催日時が過去）のイベントは、開催日時が最も古くても一覧の末尾に並ぶことを確認
  it('並び替え: 開催済みのイベントは末尾に並ぶ（既定の開催日時順）', () => {
    eventApi.list.mockReturnValue(
      of([
        makeEvent({ id: 9, name: '開催済み', startAt: '2020-01-01T10:00:00', applicationDeadline: '2019-12-25T00:00:00', open: false }),
        ...events,
      ]),
    );
    const component = createComponent() as any;

    expect(component.sortedEvents().map((e: EventSummary) => e.id)).toEqual([2, 3, 1, 9]);
  });

  // 開催日時順以外の並び順でも、開催済みのイベントは末尾に並ぶことを確認（申込数が最も多くても先頭にならない）
  it('並び替え: 申込数の多い順でも開催済みのイベントは末尾に並ぶ', () => {
    eventApi.list.mockReturnValue(
      of([
        makeEvent({ id: 9, name: '開催済み', startAt: '2020-01-01T10:00:00', acceptedCount: 99, open: false }),
        makeEvent({ id: 8, name: '開催済み2', startAt: '2021-01-01T10:00:00', acceptedCount: 50, open: false }),
        ...events,
      ]),
    );
    const component = createComponent() as any;
    component.onSortOrderChange('accepted_desc');

    // 開催前（2, 1, 3）が申込数の多い順に並び、その後ろに開催済み（9, 8）が申込数の多い順に並ぶ
    expect(component.sortedEvents().map((e: EventSummary) => e.id)).toEqual([2, 1, 3, 9, 8]);
  });

  // 申込締切を過ぎただけ（開催前）のイベントは受付終了だが、末尾には移動しないことを確認
  it('並び替え: 受付終了でも開催前のイベントは末尾に移動しない', () => {
    eventApi.list.mockReturnValue(
      of([
        makeEvent({ id: 7, name: '締切済・開催前', startAt: '2026-12-31T10:00:00', open: false }),
        ...events,
      ]),
    );
    const component = createComponent() as any;

    expect(component.sortedEvents().map((e: EventSummary) => e.id)).toEqual([7, 2, 3, 1]);
  });

  it('詳細展開: toggleExpandでイベント詳細を取得する', () => {
    const component = createComponent() as any;

    component.toggleExpand(1);

    expect(eventApi.detail).toHaveBeenCalledWith(1);
    expect(component.expandedEventId()).toBe(1);
    expect(component.expandedDetail()).not.toBeNull();
  });

  // 展開中の行をもう一度toggleExpandすると、展開状態が解除される（expandedEventIdがnullに戻る）ことを確認
  it('詳細展開: 同じ行をもう一度押すと閉じる', () => {
    const component = createComponent() as any;
    component.toggleExpand(1);

    component.toggleExpand(1);

    expect(component.expandedEventId()).toBeNull();
  });

  // apply(id)で、ApplicationApiService.apply()が正しい引数で呼ばれ、申込完了画面へnavigateすることを確認
  it('申込成功で申込完了画面へ遷移する', () => {
    const component = createComponent() as any;

    component.apply(1);

    expect(applicationApi.apply).toHaveBeenCalledWith(1, undefined, undefined);
    expect(router.navigate).toHaveBeenCalledWith(['/events', 1, 'done'], expect.objectContaining({ state: expect.anything() }));
  });

  // toggleFavorite(id)で、FavoriteStore.toggle()が対象イベントIDで呼ばれることを確認
  it('お気に入りトグルでfavoriteStore.toggleを呼ぶ', () => {
    const component = createComponent() as any;

    component.toggleFavorite(1);

    expect(favoriteStore.toggle).toHaveBeenCalledWith(1);
  });

  // お気に入り登録に成功したら、一覧を取り直さずに対象イベントのお気に入り数が1増えることを確認（Issue #13）
  it('お気に入り登録でお気に入り数が即座に1増える', () => {
    const component = createComponent() as any;
    const before = component.events().find((e: EventSummary) => e.id === 1).favoriteCount;

    component.toggleFavorite(1);

    expect(component.events().find((e: EventSummary) => e.id === 1).favoriteCount).toBe(before + 1);
    expect(eventApi.list).toHaveBeenCalledTimes(1);
  });

  // お気に入り解除に成功したら、対象イベントのお気に入り数が1減る（0未満にはならない）ことを確認（Issue #13）
  it('お気に入り解除でお気に入り数が即座に1減る', () => {
    favoriteStore.toggle.mockReturnValue(of(false));
    const component = createComponent() as any;
    const before = component.events().find((e: EventSummary) => e.id === 1).favoriteCount;

    component.toggleFavorite(1);

    expect(component.events().find((e: EventSummary) => e.id === 1).favoriteCount).toBe(Math.max(0, before - 1));
  });

  // LoginUserStore.isAdmin()がtrueを返す時、Componentのget isAdmin()もtrueを返すことを確認
  it('管理者の場合isAdminがtrueになる', () => {
    loginUserStore.isAdmin.mockReturnValue(true);

    const component = createComponent() as any;

    expect(component.isAdmin).toBe(true);
  });
});
