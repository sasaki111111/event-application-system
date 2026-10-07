// 実行環境: ブラウザ側。バックエンド（機能追加：お気に入り登録・解除・一覧、API-15〜17）を呼び出すサービス。
// HTTP通信を行うAngular標準のサービス
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
// FavoriteEventがEventSummaryを拡張（extends）するために型をインポートする
import { EventSummary } from './event-api';

export interface FavoriteResponse {
  id: number;
  eventId: number;
  createdAt: string;
}

// API-17のレスポンス1件分（EventSummaryと同じ項目＋favoritedAt、backendのFavoriteEventResponseと対応）
export interface FavoriteEvent extends EventSummary {
  favoritedAt: string;
}

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * お気に入り（登録・解除・自分の一覧取得・件数）に関するバックエンドAPIを呼び出すサービス。
 * イベント一覧・詳細画面（登録・解除ボタン）、マイページ（お気に入り一覧）から利用される想定。
 * 画面間で共有する「お気に入り登録済みIDの集合」はこのサービスではなくfavorite-store.tsが持つ
 * （このサービスはAPI呼び出しのみを担当する）。
 */
@Injectable({ providedIn: 'root' })
export class FavoriteApiService {
  constructor(private readonly http: HttpClient) {}

  /** イベントをお気に入りに登録する（API-15、冪等。既に登録済みなら200、新規なら201が返るがフロント側では区別しない）。 */
  add(eventId: number): Observable<FavoriteResponse> {
    // eventIdをボディに入れてPOSTする（登録対象のユーザーはX-User-Idヘッダから自動判定）
    return this.http.post<FavoriteResponse>(`${API_BASE_URL}/favorites`, { eventId });
  }

  /** イベントのお気に入りを解除する（API-16、冪等。未登録でも204）。 */
  remove(eventId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/favorites/${eventId}`);
  }

  /** ログイン中ユーザー本人のお気に入り一覧を取得する（API-17、本人分のみ・登録日時の降順）。 */
  myFavorites(): Observable<FavoriteEvent[]> {
    return this.http.get<FavoriteEvent[]>(`${API_BASE_URL}/my/favorites`);
  }

  /** お気に入り総数を取得する（AP-131、管理者専用）。 */
  count(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${API_BASE_URL}/favorites/count`);
  }
}
