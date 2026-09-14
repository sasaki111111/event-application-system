// 実行環境: ブラウザ側。SC-01ログイン画面（E-2）。
// 要件定義書: 「簡易ログイン（ダミー認証）。固定ユーザーは一般1名・管理者1名を基本とし、
// ログイン画面でロールを選択して切り替える」。パスワード入力は無く、ロール選択がそのままログイン。
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DummyUserStore } from '../core/dummy-user-store';

@Component({
  selector: 'app-login',
  imports: [],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly dummyUserStore = inject(DummyUserStore);
  private readonly router = inject(Router);

  protected loginAsGeneral(): void {
    this.dummyUserStore.login('1');
    this.router.navigateByUrl('/events');
  }

  protected loginAsAdmin(): void {
    this.dummyUserStore.login('2');
    this.router.navigateByUrl('/admin/events');
  }
}
