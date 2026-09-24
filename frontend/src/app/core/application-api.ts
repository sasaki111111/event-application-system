// 実行環境: ブラウザ側。バックエンド（D-3: イベント申込、D-4: 自分の申込一覧、D-5: 申込キャンセル）を呼び出すサービス。
// backendのApplicationController・各Responseと対応。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ApplicationResponse {
  id: number;
  eventId: number;
  // 機能追加（定員区分）: 区分の無いイベントへの申込はNULL
  ticketTypeId: number | null;
  userId: number;
  status: string;
  appliedAt: string;
}

// API-04のレスポンス1件分（backendのMyApplicationResponseと対応）
export interface MyApplication {
  id: number;
  eventId: number;
  eventName: string;
  startAt: string;
  status: string;
  appliedAt: string;
  // 機能追加（定員区分）: キャンセル待ちの順位（1始まり）。キャンセル待ち以外はNULL
  waitlistRank: number | null;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class ApplicationApiService {
  constructor(private readonly http: HttpClient) {}

  // API-03（一般以上、本人のuserIdに紐付け。userIdはX-User-Idヘッダから自動で決まる）。
  // ticketTypeId・extraAnswerは区分・アンケートがあるイベントの場合のみ指定する（機能追加）
  apply(eventId: number, ticketTypeId?: number, extraAnswer?: string): Observable<ApplicationResponse> {
    return this.http.post<ApplicationResponse>(`${API_BASE_URL}/applications`, {
      eventId,
      ticketTypeId,
      extraAnswer,
    });
  }

  // API-04（一般以上、本人分のみ・申込日時の降順）
  myApplications(): Observable<MyApplication[]> {
    return this.http.get<MyApplication[]>(`${API_BASE_URL}/my/applications`);
  }

  // API-05（一般以上、本人の申込のみ）
  cancel(applicationId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/applications/${applicationId}`);
  }
}
