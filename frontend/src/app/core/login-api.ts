// 実行環境: ブラウザ側。SC-010ログイン画面から呼ぶ、認証不要の窓口（機能追加）。
// ログイン前はLoginUserStoreが空でauthHeaderInterceptorがX-User-Idを付けないため、
// これらのAPI（backend側でAuthInterceptor対象外）はヘッダ無しでそのまま呼べる。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface UserResponse {
  userId: number;
  name: string;
  email: string;
  /** 利用者区分コード（ROLE_CODE参照）。画面の切り替えの判定に使う。 */
  roleCode: number;
  /** 利用者区分の表示名（コードマスタの値）。 */
  roleName: string;
}

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * 認証不要（ログイン前）のバックエンドAPI（ログイン・簡易会員登録）を呼び出すサービス。
 * ログイン画面（Login）から利用される想定。
 */
@Injectable({ providedIn: 'root' })
export class LoginApiService {
  constructor(private readonly http: HttpClient) {}

  /** メールアドレスでログインする。存在しなければbackendが401を返す。 */
  /** メールアドレスとパスワードでログインする（AP-010）。 */
  login(email: string, password: string): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE_URL}/login`, { email, password });
  }

  /** 利用者登録を行う（AP-011）。作成されるのは常に一般利用者。 */
  register(name: string, email: string, password: string): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE_URL}/users`, { name, email, password });
  }

  /** ログイン中の利用者本人のパスワードを変更する（AP-012）。成功時は本文なし（204）。 */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(`${API_BASE_URL}/my/password`, { currentPassword, newPassword });
  }
}
