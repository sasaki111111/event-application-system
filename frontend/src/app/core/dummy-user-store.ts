// 実行環境: ブラウザ側。ログイン中のユーザー（ダミー認証）を保持する入れ物（E-2）。
// パスワード認証は実装対象外（要件定義書）のため、ログイン＝ロール選択のみ。未ログイン時はnull。
// D-03: 管理者判定はバックエンドが返すrole（"admin"/"general"）に基づいて行う（userIdの決め打ちはしない）。
import { Injectable, computed, signal } from '@angular/core';

const USER_ID_STORAGE_KEY = 'dummyUserId';
const ROLE_STORAGE_KEY = 'dummyUserRole';

@Injectable({ providedIn: 'root' })
export class DummyUserStore {
  private readonly userId = signal(this.readInitial(USER_ID_STORAGE_KEY));
  private readonly role = signal(this.readInitial(ROLE_STORAGE_KEY));

  readonly currentUserId = this.userId.asReadonly();
  readonly currentRole = this.role.asReadonly();
  readonly isAdmin = computed(() => this.role() === 'admin');

  login(id: string, role: string): void {
    this.userId.set(id);
    this.role.set(role);
    try {
      localStorage.setItem(USER_ID_STORAGE_KEY, id);
      localStorage.setItem(ROLE_STORAGE_KEY, role);
    } catch {
      // プライベートブラウジング等でlocalStorageが使えなくても動作は継続する
    }
  }

  logout(): void {
    this.userId.set(null);
    this.role.set(null);
    try {
      localStorage.removeItem(USER_ID_STORAGE_KEY);
      localStorage.removeItem(ROLE_STORAGE_KEY);
    } catch {
      // プライベートブラウジング等でlocalStorageが使えなくても動作は継続する
    }
  }

  private readInitial(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }
}
