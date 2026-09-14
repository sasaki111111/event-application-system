// 実行環境: ブラウザ側。バックエンド（D-3: イベント申込）を呼び出すサービス。
// backendのApplicationController・ApplicationResponseと対応。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ApplicationResponse {
  id: number;
  eventId: number;
  userId: number;
  status: string;
  appliedAt: string;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class ApplicationApiService {
  constructor(private readonly http: HttpClient) {}

  // API-03（一般以上、本人のuserIdに紐付け。userIdはX-User-Idヘッダから自動で決まる）
  apply(eventId: number): Observable<ApplicationResponse> {
    return this.http.post<ApplicationResponse>(`${API_BASE_URL}/applications`, { eventId });
  }
}
