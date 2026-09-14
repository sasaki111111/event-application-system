// 実行環境: ブラウザ側。バックエンド（D-6: 申込実績出力）を呼び出すサービス。
// backendのReportController・EventReportResponseと対応。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface EventReport {
  eventId: number;
  eventName: string;
  startAt: string;
  capacity: number;
  acceptedCount: number;
  fillRate: number;
}

export type ReportSort = 'startAt' | 'accepted_desc';

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class ReportApiService {
  constructor(private readonly http: HttpClient) {}

  // format=json（管理者のみ）
  summary(sort: ReportSort): Observable<EventReport[]> {
    return this.http.get<EventReport[]>(`${API_BASE_URL}/reports/applications`, {
      params: { format: 'json', sort },
    });
  }

  // format=csv（管理者のみ）。X-User-Idヘッダが必要なため、直接URL遷移ではなくHttpClientでBlobとして取得する
  downloadCsv(): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/reports/applications`, {
      params: { format: 'csv' },
      responseType: 'blob',
    });
  }
}
