// 実行環境: ブラウザ側。SC-01ログイン画面から呼ぶ、認証不要の窓口（機能追加）。
// ログイン前はDummyUserStoreが空でdummyAuthInterceptorがX-User-Idを付けないため、
// これらのAPI（backend側でAuthInterceptor対象外）はヘッダ無しでそのまま呼べる。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface UserResponse {
  userId: number;
  name: string;
  email: string;
  role: string;
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
  login(email: string): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE_URL}/login`, { email });
  }

  /** 軽い会員登録を行う。作成されるのは常に一般ユーザー。 */
  register(name: string, email: string): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE_URL}/users`, { name, email });
  }
}
