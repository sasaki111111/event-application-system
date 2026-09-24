// 実行環境: ブラウザ側。バックエンド（機能追加：イベントコメント、API-20〜22）を呼び出すサービス。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

// API-20・API-21のレスポンス1件分（backendのEventCommentResponseと対応）
export interface EventComment {
  id: number;
  userName: string;
  body: string;
  createdAt: string;
  // ログイン中ユーザー本人の投稿か（削除ボタンの表示可否に使う）
  mine: boolean;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class CommentApiService {
  constructor(private readonly http: HttpClient) {}

  // API-20（投稿日時の昇順）
  list(eventId: number): Observable<EventComment[]> {
    return this.http.get<EventComment[]>(`${API_BASE_URL}/events/${eventId}/comments`);
  }

  // API-21
  post(eventId: number, body: string): Observable<EventComment> {
    return this.http.post<EventComment>(`${API_BASE_URL}/events/${eventId}/comments`, { body });
  }

  // API-22（投稿者本人または管理者のみ）
  remove(commentId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/comments/${commentId}`);
  }
}
