// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。my-applications.tsの単体テスト。
// APIはHttpClientを使わずService自体をモック（useValue）で差し替える。見た目は対象外、ロジックのみ検証する。
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApplicationApiService, MyApplication } from '../core/application-api';
import { FavoriteApiService, FavoriteEvent } from '../core/favorite-api';
import { FavoriteStore } from '../core/favorite-store';
import { MyApplications } from './my-applications';

const application: MyApplication = {
  id: 1,
  eventId: 10,
  eventName: 'テストイベント',
  startAt: '2027-01-10T10:00:00',
  statusCode: 1,
  statusName: '受付済',
  appliedAt: '2026-12-01T09:00:00',
  waitlistRank: null,
};

const favorite: FavoriteEvent = {
  id: 20,
  name: 'お気に入りイベント',
  startAt: '2027-02-01T10:00:00',
  place: '会議室',
  capacity: 10,
  applicationDeadline: '2027-01-25T00:00:00',
  acceptedCount: 1,
  open: true,
  organizerName: null,
  imageUrl: null,
  favoriteCount: 1,
  favoritedAt: '2026-12-01T09:00:00',
};

describe('MyApplications', () => {
  let applicationApi: { myApplications: ReturnType<typeof vi.fn>; cancel: ReturnType<typeof vi.fn> };
  let favoriteApi: { myFavorites: ReturnType<typeof vi.fn>; remove: ReturnType<typeof vi.fn> };
  let favoriteStore: { invalidate: ReturnType<typeof vi.fn> };

  function createComponent(): MyApplications {
    TestBed.configureTestingModule({
      providers: [
        { provide: ApplicationApiService, useValue: applicationApi },
        { provide: FavoriteApiService, useValue: favoriteApi },
        { provide: FavoriteStore, useValue: favoriteStore },
      ],
    });
    const component = TestBed.createComponent(MyApplications).componentInstance;
    component.ngOnInit();
    return component;
  }

  beforeEach(() => {
    applicationApi = {
      myApplications: vi.fn(() => of([application])),
      cancel: vi.fn(() => of(undefined)),
    };
    favoriteApi = {
      myFavorites: vi.fn(() => of([favorite])),
      remove: vi.fn(() => of(undefined)),
    };
    favoriteStore = { invalidate: vi.fn() };
  });

  it('初期表示で申込一覧を取得する', () => {
    const component = createComponent() as any;

    expect(component.applications()).toEqual([application]);
    expect(component.loading()).toBe(false);
  });

  it('初期表示でお気に入り一覧を取得する', () => {
    const component = createComponent() as any;

    expect(component.favorites()).toEqual([favorite]);
    expect(component.favoritesLoading()).toBe(false);
  });

  it('申込一覧の取得に失敗した場合はエラーメッセージを表示する', () => {
    applicationApi.myApplications.mockReturnValue(throwError(() => ({ error: { message: '取得に失敗しました。' } })));

    const component = createComponent() as any;

    expect(component.errorMessage()).toBe('取得に失敗しました。');
  });

  it('タブ切替でactiveTabが切り替わる', () => {
    const component = createComponent() as any;

    component.setTab('favorites');

    expect(component.activeTab()).toBe('favorites');
  });

  it('キャンセル確認でOKした場合、APIを呼び一覧を再取得する', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const component = createComponent() as any;
    applicationApi.myApplications.mockClear();

    component.cancel(application);

    expect(applicationApi.cancel).toHaveBeenCalledWith(application.id);
    expect(applicationApi.myApplications).toHaveBeenCalledTimes(1);
  });

  it('キャンセル確認でキャンセルした場合、APIを呼ばない', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const component = createComponent() as any;

    component.cancel(application);

    expect(applicationApi.cancel).not.toHaveBeenCalled();
  });

  it('お気に入り解除でAPIを呼び、ストアを無効化してから一覧を再取得する', () => {
    const component = createComponent() as any;
    favoriteApi.myFavorites.mockClear();

    component.unfavorite(favorite);

    expect(favoriteApi.remove).toHaveBeenCalledWith(favorite.id);
    expect(favoriteStore.invalidate).toHaveBeenCalled();
    expect(favoriteApi.myFavorites).toHaveBeenCalledTimes(1);
  });
});
