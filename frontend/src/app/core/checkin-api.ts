// 実行環境: ブラウザ側。バックエンド（機能追加：当日受付、API-18〜19）を呼び出すサービス。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

// API-18のレスポンス1件分（backendのAttendeeResponseと対応）
export interface Attendee {
  applicationId: number;
  userName: string;
  ticketTypeName: string | null;
  status: string;
  checkedInAt: string | null;
}

export interface CheckInResponse {
  applicationId: number;
  checkedInAt: string;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class CheckInApiService {
  constructor(private readonly http: HttpClient) {}

  // API-18（管理者のみ、申込日時の昇順）
  attendees(eventId: number): Observable<Attendee[]> {
    return this.http.get<Attendee[]>(`${API_BASE_URL}/events/${eventId}/attendees`);
  }

  // API-19（管理者のみ）
  checkIn(applicationId: number): Observable<CheckInResponse> {
    return this.http.put<CheckInResponse>(`${API_BASE_URL}/applications/${applicationId}/check-in`, {});
  }
}
