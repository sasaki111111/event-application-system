// 実行環境: ブラウザ側。バックエンド（D-1: 一覧・詳細、D-2: 登録・編集・削除）を呼び出すサービス。
// レスポンス・リクエストの型（API設計書 API-01・02・06・07・08）をTypeScriptの型として定義している。
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
  // 機能追加（イベント情報の拡張）
  organizerName: string | null;
  imageUrl: string | null;
  category: string | null;
}

// 機能追加（定員区分）: イベント詳細のticketTypes[]1件分（backendのTicketTypeResponseと対応）
export interface TicketType {
  id: number;
  name: string;
  capacity: number;
  acceptedCount: number;
  remaining: number;
}

export interface EventDetail extends EventSummary {
  description: string;
  remaining: number;
  // 機能追加（イベント情報の拡張・定員区分）
  extraQuestion: string | null;
  ticketTypes: TicketType[];
}

// 定員区分の登録・編集時の入力1件分（backendのTicketTypeRequestと対応、機能追加）
export interface TicketTypeRequest {
  name: string;
  capacity: number;
}

// API-06・API-07のリクエストボディ（backendのEventUpsertRequestと対応）
export interface EventUpsertRequest {
  name: string;
  startAt: string;
  place: string;
  capacity: number;
  applicationDeadline: string;
  description?: string;
  // 機能追加（イベント情報の拡張・定員区分）
  organizerName?: string;
  imageUrl?: string;
  category?: string;
  extraQuestion?: string;
  ticketTypes?: TicketTypeRequest[];
}

// 機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧1件分（backendのDeletedEventResponseと対応）
export interface DeletedEvent {
  id: number;
  name: string;
  startAt: string;
  place: string;
  capacity: number;
  deletedAt: string;
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

  // API-06（管理者のみ）
  create(request: EventUpsertRequest): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${API_BASE_URL}/events`, request);
  }

  // API-07（管理者のみ）
  update(id: number, request: EventUpsertRequest): Observable<EventDetail> {
    return this.http.put<EventDetail>(`${API_BASE_URL}/events/${id}`, request);
  }

  // API-08（管理者のみ）。実体はソフトデリート（deleted_atを立てるのみ）
  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/events/${id}`);
  }

  // 機能追加（ソフトデリート、管理者のみ）: 削除済みイベント一覧
  listDeleted(): Observable<DeletedEvent[]> {
    return this.http.get<DeletedEvent[]>(`${API_BASE_URL}/events/deleted`);
  }

  // 機能追加（ソフトデリートの復元、管理者のみ）
  restore(id: number): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${API_BASE_URL}/events/${id}/restore`, {});
  }
}
