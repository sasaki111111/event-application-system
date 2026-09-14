// 実行環境: ブラウザ側。ログイン中のユーザー（ダミー認証）を保持する入れ物（E-2）。
// パスワード認証は実装対象外（要件定義書）のため、ログイン＝ロール選択のみ。未ログイン時はnull。
import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'dummyUserId';

@Injectable({ providedIn: 'root' })
export class DummyUserStore {
  private readonly userId = signal(this.readInitial());

  readonly currentUserId = this.userId.asReadonly();

  login(id: string): void {
    this.userId.set(id);
    try {
      localStorage.setItem(STORAGE_KEY, id);
    } catch {
      // プライベートブラウジング等でlocalStorageが使えなくても動作は継続する
    }
  }

  logout(): void {
    this.userId.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // プライベートブラウジング等でlocalStorageが使えなくても動作は継続する
    }
  }

  private readInitial(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
