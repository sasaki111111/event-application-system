// 実行環境: ブラウザ側。バックエンド（申込実績出力）を呼び出すサービス。
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

/**
 * 申込実績レポート（画面表示用の集計データ取得・CSVダウンロード）に関するバックエンドAPIを
 * 呼び出すサービス。管理者の申込状況・実績画面（AdminReport）から利用される想定。
 */
@Injectable({ providedIn: 'root' })
export class ReportApiService {
  constructor(private readonly http: HttpClient) {}

  /** 画面表示用の申込実績集計を取得する（format=json、管理者のみ）。 */
  summary(sort: ReportSort): Observable<EventReport[]> {
    return this.http.get<EventReport[]>(`${API_BASE_URL}/reports/applications`, {
      params: { format: 'json', sort },
    });
  }

  /**
   * 申込実績をCSV形式でダウンロードする（format=csv、管理者のみ）。X-User-Idヘッダが必要なため、
   * 直接URL遷移（<a href>等）ではなくHttpClientでBlob（バイナリデータ）として取得する。
   */
  downloadCsv(): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/reports/applications`, {
      params: { format: 'csv' },
      responseType: 'blob',
    });
  }
}
