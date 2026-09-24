// 実行環境: ブラウザ側。バックエンド（機能追加：お気に入り登録・解除・一覧、API-15〜17）を呼び出すサービス。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
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

@Injectable({ providedIn: 'root' })
export class FavoriteApiService {
  constructor(private readonly http: HttpClient) {}

  // API-15（冪等。既に登録済みなら200、新規なら201が返るがフロント側では区別しない）
  add(eventId: number): Observable<FavoriteResponse> {
    return this.http.post<FavoriteResponse>(`${API_BASE_URL}/favorites`, { eventId });
  }

  // API-16（冪等。未登録でも204）
  remove(eventId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/favorites/${eventId}`);
  }

  // API-17（本人分のみ・登録日時の降順）
  myFavorites(): Observable<FavoriteEvent[]> {
    return this.http.get<FavoriteEvent[]>(`${API_BASE_URL}/my/favorites`);
  }
}
