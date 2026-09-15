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

@Injectable({ providedIn: 'root' })
export class LoginApiService {
  constructor(private readonly http: HttpClient) {}

  // メールアドレスでログイン。存在しなければbackendが401を返す
  login(email: string): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE_URL}/login`, { email });
  }

  // 軽い会員登録。作成されるのは常に一般ユーザー
  register(name: string, email: string): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE_URL}/users`, { name, email });
  }
}
