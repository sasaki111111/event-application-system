// 実行環境: ブラウザ側。パスワード変更画面（SC-011、/my/password）。
// ログイン中の利用者本人が、現在のパスワードと新しいパスワードを入力して自分のパスワードを変更する（F-012）。
// 項目・処理の定義は docs/30_詳細設計/30_画面詳細設計書.md のSC-011を参照。
import { Component, inject, signal } from '@angular/core';
import { LoginApiService } from '../core/login-api';

/** サーバーが返す入力エラー1件分（どの項目で、何が問題か）。 */
interface FieldError {
  field: string;
  message: string;
}

@Component({
  selector: 'app-password-change',
  imports: [],
  templateUrl: './password-change.html',
  styleUrl: './password-change.css',
})
export class PasswordChange {
  private readonly loginApi = inject(LoginApiService);

  // 各入力欄の値。signalはAngularの「値が変わると画面が自動で再描画される」入れ物
  protected readonly currentPassword = signal('');
  protected readonly newPassword = signal('');
  protected readonly confirmPassword = signal('');
  // 送信中かどうか（二重送信を防ぐためボタンを無効化する）
  protected readonly submitting = signal(false);
  // 画面上部に表示するエラーメッセージ・完了メッセージ
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  // サーバーが返した項目ごとの入力エラー
  protected readonly fieldErrors = signal<FieldError[]>([]);

  protected onCurrentPasswordInput(value: string): void {
    this.currentPassword.set(value);
  }

  protected onNewPasswordInput(value: string): void {
    this.newPassword.set(value);
  }

  protected onConfirmPasswordInput(value: string): void {
    this.confirmPassword.set(value);
  }

  /** 指定した項目の入力エラーメッセージを返す（無ければnull）。 */
  protected fieldError(field: string): string | null {
    return this.fieldErrors().find((e) => e.field === field)?.message ?? null;
  }

  /**
   * 「変更する」押下時の処理。
   * 1. 画面側のチェック（未入力、新しいパスワードと確認用の不一致）に違反した場合は、APIを呼ばずにメッセージを表示する
   * 2. パスワード変更API（AP-012）を呼び出す
   * 3. 成功したら完了メッセージを表示し、入力欄を空に戻す（ログイン状態は維持する）
   */
  protected submit(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.fieldErrors.set([]);

    // パスワードは前後の空白も含めて入力されたとおりに扱う（trimしない）
    const current = this.currentPassword();
    const next = this.newPassword();
    if (!current || !next || !this.confirmPassword()) {
      this.errorMessage.set('すべての項目を入力してください。');
      return;
    }
    if (next !== this.confirmPassword()) {
      this.errorMessage.set('新しいパスワードが一致しません。');
      return;
    }

    this.submitting.set(true);
    this.loginApi.changePassword(current, next).subscribe({
      next: () => {
        this.submitting.set(false);
        this.successMessage.set('パスワードを変更しました。');
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
      },
      error: (err) => {
        this.submitting.set(false);
        // 入力エラー（400かつerrorsあり）は項目ごとに、それ以外はサーバーのメッセージをそのまま表示する
        if (err.status === 400 && err.error?.errors) {
          this.fieldErrors.set(err.error.errors);
        } else {
          this.errorMessage.set(err.error?.message ?? 'パスワードの変更に失敗しました。');
        }
      },
    });
  }
}
