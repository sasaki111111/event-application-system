// 実行環境: ブラウザ側。お気に入り登録状況（イベントIDの集合）を画面間で共有するストア（機能追加）。
// event-list・event-detail・my-applicationsがそれぞれ独自にGET /api/my/favoritesを呼び、
// 登録・解除ロジックも別々に持っていた重複を解消するために導入した。
// 一覧そのもの（イベント名等を含む表示用データ）はmy-applicationsのお気に入りタブが個別に取得する
// （こちらはID集合のみを扱うため、それとは責務が異なる）。
//
// [Angularの基礎: Observableの.pipe()とrxjs演算子] Observableは.pipe()に演算子（operator）を
// 並べることで、値が流れる途中で加工できる。ここで使うmapは値を別の値に変換する演算子、
// tapは値はそのまま流しつつ副作用（この場合はキャッシュへの保存）だけ行う演算子、
// ofは「すでに持っている値」をObservableの形に包んで即座に流す演算子（通信をせずキャッシュを
// そのまま返したいときに使う）。
import { Injectable, computed, signal } from '@angular/core';
import { Observable, map, of, tap } from 'rxjs';
import { FavoriteApiService } from './favorite-api';

/**
 * ログイン中ユーザーのお気に入り登録状況（イベントIDの集合）を一度だけ取得し、画面間で共有する
 * キャッシュ役のストア。event-list・event-detail・my-applications等、複数の画面から
 * 同じお気に入り状態を参照・更新するために利用される想定。FavoriteApiService（API呼び出し）を
 * 内部で使い、取得結果をsignalに保持することで重複したAPI呼び出しを避ける。
 */
@Injectable({ providedIn: 'root' })
export class FavoriteStore {
  // null＝まだ取得していない（初回アクセス時に1回だけ取得する）
  private readonly ids = signal<Set<number> | null>(null);

  readonly favoriteEventIds = computed(() => this.ids() ?? new Set<number>());

  constructor(private readonly favoriteApi: FavoriteApiService) {}

  /**
   * お気に入りIDの集合を取得する。未取得ならAPIを呼んでキャッシュし、取得済みならキャッシュを
   * そのまま返す（何度呼んでも通信は最初の1回だけ）。画面表示前に呼ぶ想定。
   */
  ensureLoaded(): Observable<Set<number>> {
    // 現在のキャッシュを読む
    const cached = this.ids();
    if (cached !== null) {
      // 取得済みなら通信せず、キャッシュをObservableに包んで即座に返す
      return of(cached);
    }
    // 未取得の場合だけAPIを呼ぶ
    return this.favoriteApi.myFavorites().pipe(
      // 取得したお気に入り一覧（イベント情報付き）から、イベントIDだけを取り出したSetに変換する
      map((favorites) => new Set(favorites.map((favorite) => favorite.id))),
      // 変換したSetを、以降の呼び出しのためにキャッシュ（ids）へ保存する
      tap((ids) => this.ids.set(ids)),
    );
  }

  /** 指定イベントが（キャッシュ上で）お気に入り登録済みかどうかを返す。 */
  isFavorited(eventId: number): boolean {
    return this.favoriteEventIds().has(eventId);
  }

  /**
   * お気に入りの登録・解除を切り替える。現在の状態に応じてadd/removeのどちらかをAPI経由で呼び、
   * 成功後にキャッシュ（ids）も更新する。登録・解除はどちらも冪等（要件定義書§8 E9）。
   *
   * @returns 操作後の状態（trueなら登録済み）
   */
  toggle(eventId: number): Observable<boolean> {
    // API呼び出し成功後に、キャッシュ（ids）をfavoritedの値に合わせて更新するヘルパー関数
    const applyChange = (favorited: boolean): boolean => {
      // 元のSetを直接書き換えず、コピーしてから変更する（signalには新しいオブジェクトをsetする）
      const next = new Set(this.ids() ?? []);
      if (favorited) {
        next.add(eventId);
      } else {
        next.delete(eventId);
      }
      this.ids.set(next);
      return favorited;
    };

    if (this.isFavorited(eventId)) {
      // 既に登録済みなら解除APIを呼び、成功後にキャッシュから取り除く
      return this.favoriteApi.remove(eventId).pipe(map(() => applyChange(false)));
    }
    // 未登録なら登録APIを呼び、成功後にキャッシュへ追加する
    return this.favoriteApi.add(eventId).pipe(map(() => applyChange(true)));
  }

  /** キャッシュを破棄する。マイページのお気に入りタブなど、他の画面が独自にAPIを呼んで状態を変えた後に呼ぶ。 */
  invalidate(): void {
    // nullに戻すことで、次にensureLoaded()が呼ばれたときに再取得される
    this.ids.set(null);
  }
}
