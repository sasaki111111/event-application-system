// 実行環境: ブラウザ側。SC-01ログイン画面（E-2、機能追加でメールアドレス入力方式に変更）。
// 要件定義書: 「簡易ログイン（ダミー認証）。ログイン画面でメールアドレスを入力すると、
// そのユーザーのロールを自動判定してそれぞれの画面へ遷移する」。パスワードは無い。
// 機能追加：軽い会員登録（名前・メールだけで一般ユーザーを作成し、そのままログインする）も同じ画面に持つ。
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DummyUserStore } from '../core/dummy-user-store';
import { LoginApiService } from '../core/login-api';

interface FieldError {
  field: string;
  message: string;
}

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

  protected readonly email = signal('');
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly registerName = signal('');
  protected readonly registerEmail = signal('');
  protected readonly registering = signal(false);
  protected readonly registerErrorMessage = signal<string | null>(null);
  protected readonly registerFieldErrors = signal<FieldError[]>([]);

  protected onEmailInput(value: string): void {
    this.email.set(value);
  }

  protected login(): void {
    const email = this.email().trim();
    if (!email) {
      this.errorMessage.set('メールアドレスを入力してください。');
      return;
    }

    this.errorMessage.set(null);
    this.loading.set(true);

    this.loginApi.login(email).subscribe({
      next: (user) => {
        this.loading.set(false);
        this.dummyUserStore.login(String(user.userId), user.role);
        this.router.navigateByUrl(user.role === 'admin' ? '/admin/dashboard' : '/events');
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(
          err.status === 401 ? 'そのメールアドレスは登録されていません。' : (err.error?.message ?? 'ログインに失敗しました。'),
        );
      },
    });
  }

  protected onRegisterNameInput(value: string): void {
    this.registerName.set(value);
  }

  protected onRegisterEmailInput(value: string): void {
    this.registerEmail.set(value);
  }

  protected registerFieldError(field: string): string | null {
    return this.registerFieldErrors().find((e) => e.field === field)?.message ?? null;
  }

  // 登録できるのは常に一般ユーザー（パスワードは扱わない軽い登録のため、管理者作成の経路は用意しない）
  protected register(): void {
    this.registerErrorMessage.set(null);
    this.registerFieldErrors.set([]);
    this.registering.set(true);

    this.loginApi.register(this.registerName(), this.registerEmail()).subscribe({
      next: (created) => {
        this.registering.set(false);
        this.dummyUserStore.login(String(created.userId), created.role);
        this.router.navigateByUrl('/events');
      },
      error: (err) => {
        this.registering.set(false);
        if (err.status === 400 && err.error?.errors) {
          this.registerFieldErrors.set(err.error.errors);
        } else {
          this.registerErrorMessage.set(err.error?.message ?? '登録に失敗しました。');
        }
      },
    });
  }
}
