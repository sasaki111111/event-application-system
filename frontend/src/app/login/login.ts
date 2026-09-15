// 実行環境: ブラウザ側。SC-01ログイン画面（E-2、機能追加でID入力方式に変更）。
// 要件定義書: 「簡易ログイン（ダミー認証）。ログイン画面でユーザーIDを入力すると、
// そのIDのロールを自動判定してそれぞれの画面へ遷移する」。パスワードは無い。
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DummyUserStore } from '../core/dummy-user-store';
import { LoginApiService } from '../core/login-api';

@Component({
  selector: 'app-login',
  imports: [],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly dummyUserStore = inject(DummyUserStore);
  private readonly router = inject(Router);
  private readonly loginApi = inject(LoginApiService);

  protected readonly userId = signal('');
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected onUserIdInput(value: string): void {
    this.userId.set(value);
  }

  protected login(): void {
    const id = this.userId().trim();
    if (!id) {
      this.errorMessage.set('ユーザーIDを入力してください。');
      return;
    }

    this.errorMessage.set(null);
    this.loading.set(true);

    this.loginApi.whoAmI(id).subscribe({
      next: (who) => {
        this.loading.set(false);
        this.dummyUserStore.login(String(who.userId));
        this.router.navigateByUrl(who.admin ? '/admin/events' : '/events');
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(
          err.status === 401 ? 'そのユーザーIDは見つかりません。' : (err.error?.message ?? 'ログインに失敗しました。'),
        );
      },
    });
  }
}
