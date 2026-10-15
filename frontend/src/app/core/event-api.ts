// 実行環境: ブラウザ側。バックエンド（一覧・詳細AP-020・AP-021、登録・更新・削除・削除済み一覧・復元AP-120〜AP-124）を呼び出すサービス。
// レスポンス・リクエストの型（docs/30_詳細設計/31_API詳細設計書.md）をTypeScriptの型として定義している。
// HTTP通信を行うAngular標準のサービス
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

// AP-020のレスポンス1件分（backendのEventSummaryResponseと対応）
export interface EventSummary {
  id: number;
  name: string;
  startAt: string;
  place: string;
  capacity: number;
  applicationDeadline: string;
  acceptedCount: number;
  open: boolean;
  // 主催者名・画像URL（いずれも任意項目のためNULLあり）
  organizerName: string | null;
  imageUrl: string | null;
  // イベントのお気に入り登録件数。全利用者に返る
  favoriteCount: number;
}

// 参加区分1件分（イベント詳細・削除済みイベントのticketTypes[]。backendのTicketTypeResponseと対応）
export interface TicketType {
  id: number;
  name: string;
  capacity: number;
  acceptedCount: number;
  remaining: number;
}

// extends: TypeScriptのinterfaceが他のinterfaceのフィールドをすべて引き継ぐ構文。
// EventDetail（AP-021のレスポンス）はEventSummaryの全フィールド＋詳細画面だけで使う追加フィールドを持つ
export interface EventDetail extends EventSummary {
  description: string;
  remaining: number;
  // 申込時アンケートの質問文言（NULL＝アンケート無し）と、参加区分の一覧（区分の無いイベントは空配列）
  extraQuestion: string | null;
  ticketTypes: TicketType[];
}

// 参加区分の登録・編集時の入力1件分（backendのTicketTypeRequestと対応）
export interface TicketTypeRequest {
  name: string;
  capacity: number;
}

// AP-120・AP-121のリクエストボディ（backendのEventUpsertRequestと対応）
export interface EventUpsertRequest {
  name: string;
  startAt: string;
  place: string;
  capacity: number;
  applicationDeadline: string;
  description?: string;
  // 以下は任意項目（未指定可）
  organizerName?: string;
  imageUrl?: string;
  extraQuestion?: string;
  ticketTypes?: TicketTypeRequest[];
}

// AP-123: 管理者の「削除済みイベント」一覧1件分（backendのDeletedEventResponseと対応）
// description〜ticketTypesは、この画面（SC-122）からのイベント複製に使う項目（表示は必須としない）
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

// イベント複製時に新規登録フォームへ複写する項目。EventDetail・DeletedEventの
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

  /** イベント一覧を取得する（AP-020）。status省略時は既定でall（開催日時昇順・受付終了分も含む全件）。 */
  list(status: 'all' | 'open' = 'all'): Observable<EventSummary[]> {
    // paramsはURLのクエリ文字列になる（例: ?status=open）
    return this.http.get<EventSummary[]>(`${API_BASE_URL}/events`, {
      params: { status },
    });
  }

  /** イベント詳細を取得する（AP-021）。 */
  detail(id: number): Observable<EventDetail> {
    // イベントIDをパスに埋め込んでGETする
    return this.http.get<EventDetail>(`${API_BASE_URL}/events/${id}`);
  }

  /** イベントを新規登録する（AP-120、管理者のみ）。 */
  create(request: EventUpsertRequest): Observable<EventDetail> {
    // requestオブジェクトをそのままJSONボディとしてPOSTする
    return this.http.post<EventDetail>(`${API_BASE_URL}/events`, request);
  }

  /** イベントを編集する（AP-121、管理者のみ）。 */
  update(id: number, request: EventUpsertRequest): Observable<EventDetail> {
    // 更新対象のIDをパスに、更新後の内容をボディにしてPUTする
    return this.http.put<EventDetail>(`${API_BASE_URL}/events/${id}`, request);
  }

  /** イベントを削除する（AP-122、管理者のみ）。実体は論理削除（deleted_atを立てるのみ）。 */
  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/events/${id}`);
  }

  /** 削除済みイベント一覧を取得する（AP-123、管理者のみ）。 */
  listDeleted(): Observable<DeletedEvent[]> {
    return this.http.get<DeletedEvent[]>(`${API_BASE_URL}/events/deleted`);
  }

  /** 削除済みイベントを復元する（AP-124、管理者のみ）。 */
  restore(id: number): Observable<EventDetail> {
    // ボディは空で、対象イベントはURLのIDで指定する
    return this.http.post<EventDetail>(`${API_BASE_URL}/events/${id}/restore`, {});
  }
}
