// 実行環境: ブラウザ側。SC-01ログイン画面で入力されたIDが実在するか・どのロールかを
// backendの/api/whoami（B-5の疎通確認用エンドポイントを流用）に問い合わせる窓口。
// ログイン前はDummyUserStoreが空でdummyAuthInterceptorがX-User-Idを付けないため、
// ここでは入力されたIDを直接ヘッダーに付けて問い合わせる。
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface WhoAmI {
  userId: number;
  name: string;
  role: string;
  admin: boolean;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class LoginApiService {
  constructor(private readonly http: HttpClient) {}

  whoAmI(userId: string): Observable<WhoAmI> {
    return this.http.get<WhoAmI>(`${API_BASE_URL}/whoami`, {
      headers: new HttpHeaders({ 'X-User-Id': userId }),
    });
  }
}
