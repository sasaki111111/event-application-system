// 実行環境: ブラウザ側。backendの/api/users（GET、管理者専用のユーザー一覧）・
// /api/admins（POST、AP-25：管理者アカウント登録）を呼び出す窓口。
// ログイン・一般ユーザー登録用のPOST呼び出しはcore/login-api.tsが担当する（そちらは認証不要のため分けている）。
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

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
}
