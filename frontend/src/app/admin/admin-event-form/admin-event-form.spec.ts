// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。admin-event-form.tsの単体テスト。
// バリデーション・参加区分の追加削除・複製元の複写・保存処理のロジックを検証する。見た目は対象外。
// TestBedはAngularのテスト用ユーティリティ。本物のDI（依存性注入）の仕組みを使って
// テスト対象のComponentを組み立てつつ、依存するService（EventApiService等）だけを
// テスト用のモック（ダミー実装）に差し替えられる。
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
// of()/throwError()はRxJS（Observableを扱うライブラリ）のテスト用ヘルパー。
// of(値) は即座に成功して値を1つ流すObservableを作り、throwError(() => エラー) は
// 即座に失敗するObservableを作る。実際のAPI通信（HTTP）の代わりにこれらを使う。
import { of, throwError } from 'rxjs';
import { EventApiService, EventDetail, EventDuplicateSource } from '../../core/event-api';
import { AdminEventForm } from './admin-event-form';

const detail: EventDetail = {
  id: 5,
  name: '既存イベント',
  startAt: '2027-01-10T10:00:00',
  place: '会議室',
  capacity: 10,
  applicationDeadline: '2027-01-05T00:00:00',
  acceptedCount: 0,
  open: true,
  organizerName: '主催団体',
  imageUrl: null,
  favoriteCount: 0,
  description: '説明文',
  remaining: 10,
  extraQuestion: null,
  ticketTypes: [],
};

const duplicateSource: EventDuplicateSource = {
  name: '複製元イベント',
  place: '第2会議室',
  capacity: 20,
  description: '複製元の説明',
  organizerName: '複製元主催',
  imageUrl: 'https://example.com/a.png',
  extraQuestion: '参加動機は？',
  ticketTypes: [
    { id: 1, name: '一般枠', capacity: 10, acceptedCount: 0, remaining: 10 },
    { id: 2, name: '優先枠', capacity: 10, acceptedCount: 0, remaining: 10 },
  ],
};

describe('AdminEventForm', () => {
  // vi.fn()はVitest（テストランナー）が提供するモック関数。呼ばれたかどうか・何を渡されたかを
  // 記録でき、mockReturnValue等で戻り値も自由に差し替えられる。
  let eventApi: { detail: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };
  let router: { getCurrentNavigation: ReturnType<typeof vi.fn>; navigateByUrl: ReturnType<typeof vi.fn> };

  /**
   * テスト対象のAdminEventFormを、指定したルートパラメータ（:id）で初期化して返すヘルパー。
   * ActivatedRouteをモックに差し替えることで、実際のURL遷移無しに
   * 「idが無い（新規登録）」「idがある（編集）」の両パターンを再現できる。
   * ngOnInit()は本来Angularが自動的に呼ぶものだが、テストでは明示的に呼び出している。
   * @param idParam ルートの:idパラメータ相当の値。nullなら新規登録モード
   */
  function createComponent(idParam: string | null): AdminEventForm {
    TestBed.configureTestingModule({
      providers: [
        { provide: EventApiService, useValue: eventApi },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => idParam } } } },
      ],
    });
    const component = TestBed.createComponent(AdminEventForm).componentInstance;
    component.ngOnInit();
    return component;
  }

  // 各テスト（it）の前に毎回実行され、モックを初期状態に戻す（前のテストの影響を持ち越さないため）
  beforeEach(() => {
    eventApi = {
      detail: vi.fn(() => of(detail)),
      create: vi.fn(() => of(detail)),
      update: vi.fn(() => of(detail)),
    };
    router = { getCurrentNavigation: vi.fn(() => null), navigateByUrl: vi.fn() };
  });

  it('新規登録モード: idが無ければeditModeはfalse', () => {
    const component = createComponent(null) as any;

    expect(component.isEditMode).toBe(false);
    expect(eventApi.detail).not.toHaveBeenCalled();
  });

  it('編集モード: idがあればイベント詳細を取得しフォームへ反映する', () => {
    const component = createComponent('5') as any;

    expect(eventApi.detail).toHaveBeenCalledWith(5);
    expect(component.isEditMode).toBe(true);
    expect(component.form.value.name).toBe('既存イベント');
    expect(component.form.value.organizerName).toBe('主催団体');
  });

  it('バリデーション: 必須項目が未入力の場合form.invalid', () => {
    const component = createComponent(null) as any;

    expect(component.form.invalid).toBe(true);

    component.form.patchValue({
      name: '新規イベント',
      startAt: '2027-05-01T10:00',
      place: '会場',
      capacity: 5,
      applicationDeadline: '2027-04-01T00:00',
    });

    expect(component.form.valid).toBe(true);
  });

  it('参加区分: addTicketTypeで行が増え、removeTicketTypeで減る', () => {
    const component = createComponent(null) as any;

    component.addTicketType();
    component.addTicketType();
    expect(component.ticketTypesArray.length).toBe(2);

    component.removeTicketType(0);
    expect(component.ticketTypesArray.length).toBe(1);
  });

  it('参加区分の定員合計が正しく計算される', () => {
    const component = createComponent(null) as any;
    component.addTicketType();
    component.addTicketType();
    component.ticketTypesArray.at(0).patchValue({ capacity: 3 });
    component.ticketTypesArray.at(1).patchValue({ capacity: 7 });

    expect(component.ticketTypesCapacitySum).toBe(10);
  });

  it('複製元がある場合、開催日時・申込締切を除く項目が複写される', () => {
    // mockReturnValueで「複製」ボタンから渡されるNavigation stateを再現し、
    // router.getCurrentNavigation()が複製元データを返す状況を作る
    router.getCurrentNavigation.mockReturnValue({ extras: { state: { duplicateFrom: duplicateSource } } });

    const component = createComponent(null) as any;

    expect(component.duplicatedFromName).toBe('複製元イベント');
    expect(component.form.value.name).toBe('複製元イベント');
    expect(component.form.value.place).toBe('第2会議室');
    expect(component.form.value.organizerName).toBe('複製元主催');
    expect(component.form.value.extraQuestion).toBe('参加動機は？');
    // 開催日時・申込締切は複写対象外（未入力のまま）
    expect(component.form.value.startAt).toBe('');
    expect(component.form.value.applicationDeadline).toBe('');
    // 参加区分もコピーされる
    expect(component.ticketTypesArray.length).toBe(2);
    expect(component.ticketTypesArray.at(0).value.name).toBe('一般枠');
  });

  it('複製元が無い場合は何も複写されない', () => {
    const component = createComponent(null) as any;

    expect(component.duplicatedFromName).toBeNull();
    expect(component.form.value.name).toBe('');
    expect(component.ticketTypesArray.length).toBe(0);
  });

  it('保存成功時はイベント一覧へ遷移する', () => {
    const component = createComponent(null) as any;
    component.form.patchValue({
      name: '新規イベント',
      startAt: '2027-05-01T10:00',
      place: '会場',
      capacity: 5,
      applicationDeadline: '2027-04-01T00:00',
    });

    component.submit();

    expect(eventApi.create).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/events');
  });

  it('保存時に入力値エラー（400）が返るとfieldErrorsに反映される', () => {
    // mockReturnValueでcreate()の戻り値を「400エラーを返すObservable」に差し替え、
    // サーバー側バリデーションエラーが発生した状況を再現する
    eventApi.create.mockReturnValue(
      throwError(() => ({ status: 400, error: { errors: [{ field: 'name', message: 'イベント名を入力してください' }] } })),
    );
    const component = createComponent(null) as any;
    component.form.patchValue({
      name: '新規イベント',
      startAt: '2027-05-01T10:00',
      place: '会場',
      capacity: 5,
      applicationDeadline: '2027-04-01T00:00',
    });

    component.submit();

    expect(component.fieldErrors()).toEqual([{ field: 'name', message: 'イベント名を入力してください' }]);
    expect(component.saving()).toBe(false);
  });
});
