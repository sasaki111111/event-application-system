// 実行環境: ブラウザ側。バックエンド（機能追加：当日受付、API-18〜19）を呼び出すサービス。
// HTTP通信を行うAngular標準のサービス（詳細はapplication-api.tsを参照）
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

// API-18のレスポンス1件分（backendのAttendeeResponseと対応）
export interface Attendee {
  applicationId: number;
  userName: string;
  ticketTypeName: string | null;
  /** 申込状況コード（STATUS_CODE参照）。表示の切り替えの判定に使う。 */
  statusCode: number;
  /** 申込状況の表示名（コードマスタの値）。 */
  statusName: string;
  checkedInAt: string | null;
  // （機能追加）: 申込時アンケートへの回答。アンケート未設定・未回答の場合はNULL
  extraAnswer: string | null;
}

// checkIn()の戻り値1件分。チェックイン操作が反映された後の最新状態を表す
export interface CheckInResponse {
  applicationId: number;
  checkedInAt: string;
}

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * 当日受付（会場でのチェックイン）に関するバックエンドAPIを呼び出すサービス。
 * 管理者のイベント当日受付画面（AdminCheckin）から利用される想定。
 */
@Injectable({ providedIn: 'root' })
export class CheckInApiService {
  constructor(private readonly http: HttpClient) {}

  /** 指定イベントの申込者（受付対象者）一覧を取得する（API-18、管理者のみ、申込日時の昇順）。 */
  attendees(eventId: number): Observable<Attendee[]> {
    // 指定イベントIDの申込者一覧をGETで取得する
    return this.http.get<Attendee[]>(`${API_BASE_URL}/events/${eventId}/attendees`);
  }

  /** 指定の申込をチェックイン済みにする（API-19、管理者のみ）。 */
  checkIn(applicationId: number): Observable<CheckInResponse> {
    // PUTで該当申込のチェックイン状態を更新する（ボディは空でよい。対象は申込IDで指定済みのため）
    return this.http.put<CheckInResponse>(`${API_BASE_URL}/applications/${applicationId}/check-in`, {});
  }
}
