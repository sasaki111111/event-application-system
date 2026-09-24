// 実行環境: ブラウザ側。お気に入り登録状況（イベントIDの集合）を画面間で共有するストア（機能追加）。
// event-list・event-detail・my-applicationsがそれぞれ独自にGET /api/my/favoritesを呼び、
// 登録・解除ロジックも別々に持っていた重複を解消するために導入した。
// 一覧そのもの（イベント名等を含む表示用データ）はmy-applicationsのお気に入りタブが個別に取得する
// （こちらはID集合のみを扱うため、それとは責務が異なる）。
import { Injectable, computed, signal } from '@angular/core';
import { Observable, map, of, tap } from 'rxjs';
import { FavoriteApiService } from './favorite-api';

@Injectable({ providedIn: 'root' })
export class FavoriteStore {
  // null＝まだ取得していない（初回アクセス時に1回だけ取得する）
  private readonly ids = signal<Set<number> | null>(null);

  readonly favoriteEventIds = computed(() => this.ids() ?? new Set<number>());

  constructor(private readonly favoriteApi: FavoriteApiService) {}

  // 未取得なら取得し、取得済みならキャッシュをそのまま返す
  ensureLoaded(): Observable<Set<number>> {
    const cached = this.ids();
    if (cached !== null) {
      return of(cached);
    }
    return this.favoriteApi.myFavorites().pipe(
      map((favorites) => new Set(favorites.map((favorite) => favorite.id))),
      tap((ids) => this.ids.set(ids)),
    );
  }

  isFavorited(eventId: number): boolean {
    return this.favoriteEventIds().has(eventId);
  }

  // 登録・解除はどちらも冪等（要件定義書§8 E9）。成功後の状態（trueなら登録済み）を返す
  toggle(eventId: number): Observable<boolean> {
    const applyChange = (favorited: boolean): boolean => {
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
      return this.favoriteApi.remove(eventId).pipe(map(() => applyChange(false)));
    }
    return this.favoriteApi.add(eventId).pipe(map(() => applyChange(true)));
  }

  // マイページのお気に入りタブなど、他の画面が独自にAPIを呼んで状態を変えた後にキャッシュを破棄する
  invalidate(): void {
    this.ids.set(null);
  }
}
