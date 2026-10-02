// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。event-search.tsの単体テスト。
// 追加した並び替えロジック（フロント側のみの計算）を中心に検証する。画面の見た目は対象外。
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { EventApiService, EventSummary } from '../../core/event-api';
import { EventSearch } from './event-search';

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

describe('EventSearch', () => {
  const events: EventSummary[] = [
    makeEvent({ id: 1, name: 'ボードゲーム大会', place: '第1会議室', startAt: '2027-03-03T10:00:00', applicationDeadline: '2027-03-01T00:00:00', acceptedCount: 3, favoriteCount: 1 }),
    makeEvent({ id: 2, name: '読書会', place: '図書室', startAt: '2027-01-01T10:00:00', applicationDeadline: '2027-03-05T00:00:00', acceptedCount: 10, favoriteCount: 5 }),
    makeEvent({ id: 3, name: '料理教室', place: '第2会議室', startAt: '2027-02-02T10:00:00', applicationDeadline: '2027-01-20T00:00:00', acceptedCount: 1, favoriteCount: 3 }),
  ];

  function createComponent(): EventSearch {
    TestBed.configureTestingModule({
      providers: [{ provide: EventApiService, useValue: { list: () => of(events) } }],
    });
    const component = TestBed.createComponent(EventSearch).componentInstance;
    component.ngOnInit();
    return component;
  }

  // 絞り込み条件が未入力の初期状態では、全件が開催日時の昇順で返ることを確認
  it('初期状態では開催日時昇順で全件を返す', () => {
    const component = createComponent() as any;

    expect(component.filteredEvents().map((e: EventSummary) => e.id)).toEqual([2, 3, 1]);
  });

  // onKeywordInput()でイベント名に部分一致するものだけに絞り込まれることを確認
  it('キーワードでイベント名を絞り込む', () => {
    const component = createComponent() as any;

    component.onKeywordInput('読書');

    expect(component.filteredEvents().map((e: EventSummary) => e.id)).toEqual([2]);
  });

  // キーワードは場所（place）にも部分一致し、大文字小文字を区別しないことを確認
  it('キーワードで場所を絞り込む（大文字小文字を区別しない）', () => {
    const component = createComponent() as any;

    component.onKeywordInput('第1');

    expect(component.filteredEvents().map((e: EventSummary) => e.id)).toEqual([1]);
  });

  // onDateFromInput()/onDateToInput()で指定した開催日の範囲内のイベントだけに絞り込まれることを確認
  it('開催日（から・まで）で絞り込む', () => {
    const component = createComponent() as any;

    component.onDateFromInput('2027-02-01');
    component.onDateToInput('2027-02-28');

    expect(component.filteredEvents().map((e: EventSummary) => e.id)).toEqual([3]);
  });

  // clearFilters()で入力済みの絞り込み条件がすべてクリアされ、全件表示に戻ることを確認
  it('条件をクリアすると絞り込みが解除される', () => {
    const component = createComponent() as any;
    component.onKeywordInput('読書');

    component.clearFilters();

    expect(component.filteredEvents()).toHaveLength(3);
  });

  // onSortOrderChange('accepted_desc')で、申込数（acceptedCount）の降順に並び替わることを確認
  it('並び替え: 申込数の多い順', () => {
    const component = createComponent() as any;

    component.onSortOrderChange('accepted_desc');

    expect(component.filteredEvents().map((e: EventSummary) => e.id)).toEqual([2, 1, 3]);
  });

  // onSortOrderChange('favorite_desc')で、お気に入り数の降順に並び替わることを確認
  it('並び替え: お気に入り数の多い順', () => {
    const component = createComponent() as any;

    component.onSortOrderChange('favorite_desc');

    expect(component.filteredEvents().map((e: EventSummary) => e.id)).toEqual([2, 3, 1]);
  });

  // onSortOrderChange('deadline_asc')で、申込締切の昇順（近い順）に並び替わることを確認
  it('並び替え: 申込締切が近い順', () => {
    const component = createComponent() as any;

    component.onSortOrderChange('deadline_asc');

    expect(component.filteredEvents().map((e: EventSummary) => e.id)).toEqual([3, 1, 2]);
  });
});
