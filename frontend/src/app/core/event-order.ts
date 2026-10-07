// 実行環境: ブラウザ側。イベントの一覧表示で共通して使う並び順の処理。
// イベント一覧（SC-020）とイベント検索（SC-021）は、どの並び順でも開催済みのイベントを末尾にまとめて表示する
// （これから開催されるイベントを先に見せるため。docs/30_詳細設計/30_画面詳細設計書.md SC-020・SC-021）。

/**
 * 開催済み（開催日時が現在時刻以前）かどうかを判定する。
 *
 * @param startAt 開催日時（日本時間の「yyyy-MM-ddTHH:mm:ss」形式）
 * @param now     判定の基準とする現在時刻
 */
export function isHeld(startAt: string, now: Date): boolean {
  return new Date(startAt).getTime() <= now.getTime();
}

/**
 * 並び替え済みのイベントの配列を、順序を保ったまま「開催前 → 開催済み」の順に並べ直した新しい配列を返す。
 * 開催前・開催済みそれぞれの中の順序は、渡された配列の順序（＝選択された並び順）のままになる。
 *
 * @param events 並び替え済みのイベント
 * @param now    判定の基準とする現在時刻（省略時は呼び出した時点の時刻）
 */
export function moveHeldEventsLast<T extends { startAt: string }>(events: T[], now: Date = new Date()): T[] {
  const upcoming = events.filter((event) => !isHeld(event.startAt, now));
  const held = events.filter((event) => isHeld(event.startAt, now));
  return [...upcoming, ...held];
}
