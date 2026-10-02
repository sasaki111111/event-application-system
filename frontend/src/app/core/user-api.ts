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
  // AP-34: 匿名化（退会）済みかどうか。未退会はnull
  anonymizedAt: string | null;
}

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * 利用者管理（一覧・詳細・管理者登録・降格・退会）に関するバックエンドAPIを呼び出すサービス。
 * 管理者の利用者一覧・利用者詳細画面（AdminUserList、AdminUserDetail）、およびマイページの
 * 退会処理から利用される想定。
 */
@Injectable({ providedIn: 'root' })
export class UserApiService {
  constructor(private readonly http: HttpClient) {}

  /** 利用者一覧を取得する（管理者専用）。 */
  list(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${API_BASE_URL}/users`);
  }

  /** 管理者アカウントを登録する（AP-25、管理者のみ。作成されるのは常に管理者）。 */
  registerAdmin(name: string, email: string): Observable<UserSummary> {
    return this.http.post<UserSummary>(`${API_BASE_URL}/admins`, { name, email });
  }

  /** 利用者の基本情報を取得する（AP-26、管理者専用、SC-15利用者詳細）。 */
  getById(userId: number): Observable<UserSummary> {
    return this.http.get<UserSummary>(`${API_BASE_URL}/users/${userId}`);
  }

  /** 指定利用者の申込一覧を取得する（AP-27、管理者専用、SC-15利用者詳細。AP-13と同形式）。 */
  applicationsOf(userId: number): Observable<MyApplication[]> {
    return this.http.get<MyApplication[]>(`${API_BASE_URL}/users/${userId}/applications`);
  }

  /** 指定利用者のお気に入り一覧を取得する（AP-28、管理者専用、SC-15利用者詳細。AP-18と同形式）。 */
  favoritesOf(userId: number): Observable<FavoriteEvent[]> {
    return this.http.get<FavoriteEvent[]>(`${API_BASE_URL}/users/${userId}/favorites`);
  }

  /** 指定利用者のコメント履歴を取得する（AP-31、管理者専用、SC-15利用者詳細）。 */
  commentsOf(userId: number): Observable<UserComment[]> {
    return this.http.get<UserComment[]>(`${API_BASE_URL}/users/${userId}/comments`);
  }

  /** 対象の管理者を一般利用者に変更する（AP-33、管理者専用、SC-15利用者詳細から呼び出す）。 */
  demote(userId: number): Observable<UserSummary> {
    return this.http.put<UserSummary>(`${API_BASE_URL}/users/${userId}/demote`, {});
  }

  /**
   * 利用者を匿名化（退会）する（AP-34、本人または管理者）。本人の場合はSC-06（マイページ）から、
   * 管理者が他の利用者を対象にする場合はSC-15（利用者詳細）から呼び出す。
   */
  anonymize(userId: number): Observable<UserSummary> {
    return this.http.delete<UserSummary>(`${API_BASE_URL}/users/${userId}`);
  }
}
