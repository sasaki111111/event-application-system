// 実行環境: ブラウザ側。バックエンド（D-1: GET /api/events, GET /api/events/{id}）を呼び出すサービス。
// レスポンスの型（API設計書 API-01・API-02）をTypeScriptの型として定義している。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface EventSummary {
  id: number;
  name: string;
  startAt: string;
  place: string;
  capacity: number;
  applicationDeadline: string;
  acceptedCount: number;
  open: boolean;
}

export interface EventDetail extends EventSummary {
  description: string;
  remaining: number;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class EventApiService {
  constructor(private readonly http: HttpClient) {}

  // status省略時は既定でall（開催日時昇順・受付終了分も含む全件）
  list(status: 'all' | 'open' = 'all'): Observable<EventSummary[]> {
    return this.http.get<EventSummary[]>(`${API_BASE_URL}/events`, {
      params: { status },
    });
  }

  detail(id: number): Observable<EventDetail> {
    return this.http.get<EventDetail>(`${API_BASE_URL}/events/${id}`);
  }
}
