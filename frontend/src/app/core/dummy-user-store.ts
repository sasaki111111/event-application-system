// 実行環境: ブラウザ側。SC-01（ログイン画面）が無い間、「今どのユーザーとして操作しているか」を保持する暫定の入れ物。
// ログイン画面ができたら、ここではなく実際のログイン結果を使うように置き換える。
import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'dummyUserId';

@Injectable({ providedIn: 'root' })
export class DummyUserStore {
  private readonly userId = signal(this.readInitial());

  readonly currentUserId = this.userId.asReadonly();

  setUserId(id: string): void {
    this.userId.set(id);
    try {
      localStorage.setItem(STORAGE_KEY, id);
    } catch {
      // プライベートブラウジング等でlocalStorageが使えなくても動作は継続する
    }
  }

  private readInitial(): string {
    try {
      return localStorage.getItem(STORAGE_KEY) ?? '1';
    } catch {
      return '1';
    }
  }
}
