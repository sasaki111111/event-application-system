// 実行環境: テスト実行時。開催済みのイベントを末尾に並べる共通処理（event-order.ts）の単体テスト。
import { isHeld, moveHeldEventsLast } from './event-order';

describe('event-order', () => {
  // 判定の基準とする現在時刻を固定する（実行した日時に結果が左右されないようにするため）
  const now = new Date('2026-10-07T12:00:00');

  it('isHeld: 開催日時が現在時刻より前なら開催済み', () => {
    expect(isHeld('2026-10-07T11:59:59', now)).toBe(true);
  });

  it('isHeld: 開催日時が現在時刻ちょうどなら開催済み（境界）', () => {
    expect(isHeld('2026-10-07T12:00:00', now)).toBe(true);
  });

  it('isHeld: 開催日時が現在時刻より後なら開催前', () => {
    expect(isHeld('2026-10-07T12:00:01', now)).toBe(false);
  });

  it('moveHeldEventsLast: 開催済みを末尾に移し、それぞれの中の順序は保つ', () => {
    const events = [
      { id: 1, startAt: '2026-10-01T10:00:00' }, // 開催済み
      { id: 2, startAt: '2026-11-01T10:00:00' }, // 開催前
      { id: 3, startAt: '2026-09-01T10:00:00' }, // 開催済み
      { id: 4, startAt: '2026-10-20T10:00:00' }, // 開催前
    ];

    expect(moveHeldEventsLast(events, now).map((e) => e.id)).toEqual([2, 4, 1, 3]);
  });

  it('moveHeldEventsLast: 開催済みが無ければ順序は変わらず、元の配列は変更しない', () => {
    const events = [
      { id: 2, startAt: '2026-11-01T10:00:00' },
      { id: 4, startAt: '2026-10-20T10:00:00' },
    ];

    const result = moveHeldEventsLast(events, now);

    expect(result.map((e) => e.id)).toEqual([2, 4]);
    expect(result).not.toBe(events);
  });

  it('moveHeldEventsLast: すべて開催済み・空の配列でも動作する', () => {
    expect(moveHeldEventsLast([{ id: 1, startAt: '2020-01-01T10:00:00' }], now).map((e) => e.id)).toEqual([1]);
    expect(moveHeldEventsLast([], now)).toEqual([]);
  });
});
