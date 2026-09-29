// 実行環境: ブラウザ側。backendの/api/users（GET、管理者専用のユーザー一覧）・
// /api/admins（POST、AP-25：管理者アカウント登録）を呼び出す窓口。
// ログイン・一般ユーザー登録用のPOST呼び出しはcore/login-api.tsが担当する（そちらは認証不要のため分けている）。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MyApplication } from './application-api';
import { FavoriteEvent } from './favorite-api';
import { UserComment } from './comment-api';

export interface UserSummary {
  userId: number;
  name: string;
  email: string;
  role: string;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class UserApiService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${API_BASE_URL}/users`);
  }

  // AP-25: 管理者のみ。作成されるのは常に管理者
  registerAdmin(name: string, email: string): Observable<UserSummary> {
    return this.http.post<UserSummary>(`${API_BASE_URL}/admins`, { name, email });
  }

  // AP-26（D-13）: 管理者専用。SC-15利用者詳細の基本情報取得
  getById(userId: number): Observable<UserSummary> {
    return this.http.get<UserSummary>(`${API_BASE_URL}/users/${userId}`);
  }

  // AP-27（D-13）: 管理者専用。SC-15利用者詳細の申込一覧（AP-13と同形式）
  applicationsOf(userId: number): Observable<MyApplication[]> {
    return this.http.get<MyApplication[]>(`${API_BASE_URL}/users/${userId}/applications`);
  }

  // AP-28（D-13）: 管理者専用。SC-15利用者詳細のお気に入り一覧（AP-18と同形式）
  favoritesOf(userId: number): Observable<FavoriteEvent[]> {
    return this.http.get<FavoriteEvent[]>(`${API_BASE_URL}/users/${userId}/favorites`);
  }

  // AP-31（D-21）: 管理者専用。SC-15利用者詳細のコメント履歴
  commentsOf(userId: number): Observable<UserComment[]> {
    return this.http.get<UserComment[]>(`${API_BASE_URL}/users/${userId}/comments`);
  }
}
