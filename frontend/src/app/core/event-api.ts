// 実行環境: ブラウザ側。バックエンド（一覧・詳細、登録・編集・削除）を呼び出すサービス。
// レスポンス・リクエストの型（API設計書 API-01・02・06・07・08）をTypeScriptの型として定義している。
// HTTP通信を行うAngular標準のサービス
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
  // （機能追加）: イベントのお気に入り登録件数。全利用者に返る
  favoriteCount: number;
}

// 機能追加（定員区分）: イベント詳細のticketTypes[]1件分（backendのTicketTypeResponseと対応）
export interface TicketType {
  id: number;
  name: string;
  capacity: number;
  acceptedCount: number;
  remaining: number;
}

// extends: TypeScriptのinterfaceが他のinterfaceのフィールドをすべて引き継ぐ構文。
// EventDetailはEventSummaryの全フィールド＋詳細画面だけで使う追加フィールドを持つ
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
  extraQuestion?: string;
  ticketTypes?: TicketTypeRequest[];
}

// 機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧1件分（backendのDeletedEventResponseと対応）
// description〜ticketTypesは、この画面からのイベント複製に必要な項目として追加（表示は必須としない）
export interface DeletedEvent {
  id: number;
  name: string;
  startAt: string;
  place: string;
  capacity: number;
  deletedAt: string;
  description: string | null;
  organizerName: string | null;
  imageUrl: string | null;
  extraQuestion: string | null;
  ticketTypes: TicketType[];
}

// （機能追加）: イベント複製時に新規登録フォームへ複写する項目。EventDetail・DeletedEventの
// いずれも構造的にこの形を満たす（開催日時・申込締切は複製対象から除く）
export interface EventDuplicateSource {
  name: string;
  place: string;
  capacity: number;
  description: string | null;
  organizerName: string | null;
  imageUrl: string | null;
  extraQuestion: string | null;
  ticketTypes: TicketType[];
}

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * イベント本体（一覧・詳細取得、登録・編集・削除・復元）に関するバックエンドAPIを呼び出すサービス。
 * イベント一覧・検索・詳細画面、管理者のイベント管理・登録編集・削除済み一覧画面から利用される想定。
 */
@Injectable({ providedIn: 'root' })
export class EventApiService {
  constructor(private readonly http: HttpClient) {}

  /** イベント一覧を取得する（API-01）。status省略時は既定でall（開催日時昇順・受付終了分も含む全件）。 */
  list(status: 'all' | 'open' = 'all'): Observable<EventSummary[]> {
    // paramsはURLのクエリ文字列になる（例: ?status=open）
    return this.http.get<EventSummary[]>(`${API_BASE_URL}/events`, {
      params: { status },
    });
  }

  /** イベント詳細を取得する（API-02）。 */
  detail(id: number): Observable<EventDetail> {
    // イベントIDをパスに埋め込んでGETする
    return this.http.get<EventDetail>(`${API_BASE_URL}/events/${id}`);
  }

  /** イベントを新規登録する（API-06、管理者のみ）。 */
  create(request: EventUpsertRequest): Observable<EventDetail> {
    // requestオブジェクトをそのままJSONボディとしてPOSTする
    return this.http.post<EventDetail>(`${API_BASE_URL}/events`, request);
  }

  /** イベントを編集する（API-07、管理者のみ）。 */
  update(id: number, request: EventUpsertRequest): Observable<EventDetail> {
    // 更新対象のIDをパスに、更新後の内容をボディにしてPUTする
    return this.http.put<EventDetail>(`${API_BASE_URL}/events/${id}`, request);
  }

  /** イベントを削除する（API-08、管理者のみ）。実体はソフトデリート（deleted_atを立てるのみ）。 */
  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/events/${id}`);
  }

  /** 削除済みイベント一覧を取得する（機能追加・ソフトデリート、管理者のみ）。 */
  listDeleted(): Observable<DeletedEvent[]> {
    return this.http.get<DeletedEvent[]>(`${API_BASE_URL}/events/deleted`);
  }

  /** 削除済みイベントを復元する（機能追加・ソフトデリートの復元、管理者のみ）。 */
  restore(id: number): Observable<EventDetail> {
    // ボディは空で、対象イベントはURLのIDで指定する
    return this.http.post<EventDetail>(`${API_BASE_URL}/events/${id}/restore`, {});
  }
}
