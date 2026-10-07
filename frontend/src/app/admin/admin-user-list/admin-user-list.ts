// 実行環境: ブラウザ側。SC-140 利用者管理画面（/admin/users）。
// 登録済み利用者の一覧（AP-140）と、管理者アカウント登録フォーム（AP-145）を提供する。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserApiService, UserSummary } from '../../core/user-api';
import { ROLE_CODE } from '../../core/codes';

interface FieldError {
  field: string;
  message: string;
}

@Component({
  selector: 'app-admin-user-list',
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-user-list.html',
  styleUrl: './admin-user-list.css',
})
/**
 * SC-140 利用者管理画面（/admin/users）を担当するComponent。
 * 登録済み利用者の一覧表示と、管理者アカウントの新規登録フォームを1画面で提供する。
 *
 * - ルーティング定義（app.routes.ts）でこのパスには authGuard・adminGuard が設定されており、
 *   管理者以外のユーザーはアクセスできない（＝管理者専用画面）。
 * - 利用するAngular Service: UserApiService（利用者一覧の取得、管理者アカウントの登録）。
 * - 画面遷移: 一覧の各行の利用者名（[routerLink]）から /admin/users/:id（AdminUserDetail）へ遷移する。
 */
export class AdminUserList implements OnInit {
  // テンプレートで利用者区分コードを判定するために公開する
  protected readonly RoleCode = ROLE_CODE;
  protected readonly users = signal<UserSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  // SC-140: 管理者アカウント登録フォーム（AP-145）
  protected readonly registerName = signal('');
  protected readonly registerEmail = signal('');
  // 管理者アカウント登録フォームの初期パスワード入力値
  protected readonly registerPassword = signal('');
  protected readonly registering = signal(false);
  protected readonly registerErrorMessage = signal<string | null>(null);
  protected readonly registerFieldErrors = signal<FieldError[]>([]);

  constructor(private readonly userApi: UserApiService) {}

  /**
   * Angularのライフサイクルフックの一つ。Componentが画面に表示される直前に一度だけ実行される。
   * ここでは利用者一覧の初回読み込みを行う。
   */
  ngOnInit(): void {
    this.loadUsers();
  }

  /** 管理者登録フォームの「名前」入力欄（(input)="onRegisterNameInput(...)"）から呼ばれる処理。 */
  protected onRegisterNameInput(value: string): void {
    this.registerName.set(value);
  }

  /** 管理者登録フォームの「メールアドレス」入力欄（(input)="onRegisterEmailInput(...)"）から呼ばれる処理。 */
  protected onRegisterEmailInput(value: string): void {
    this.registerEmail.set(value);
  }

  /** 指定した項目名（field）について、管理者登録フォームに表示すべきサーバー側エラーを返す（無ければnull）。 */
  /** 初期パスワード入力欄の入力値をsignalに反映する。 */
  protected onRegisterPasswordInput(value: string): void {
    this.registerPassword.set(value);
  }

  protected registerFieldError(field: string): string | null {
    return this.registerFieldErrors().find((e) => e.field === field)?.message ?? null;
  }

  /**
   * 管理者登録フォームの送信（(submit)="...registerAdmin()"）から呼ばれる処理。
   * AP-145: 既存の管理者のみ実行できる。作成されるのは常に管理者。
   * 名前・メールアドレスの入力チェックはサーバー側で行われ、400エラー＋errors配列が
   * 返ってきた場合にregisterFieldErrorsへ反映して各入力欄の下にエラー文を表示する。
   */
  protected registerAdmin(): void {
    // 前回表示していたエラーをクリアし、登録中状態にする
    this.registerErrorMessage.set(null);
    this.registerFieldErrors.set([]);
    this.registering.set(true);

    // 入力されている名前・メールアドレスで管理者登録APIを呼び出す
    this.userApi.registerAdmin(this.registerName(), this.registerEmail(), this.registerPassword()).subscribe({
      next: () => {
        // 成功したら登録中状態を解除し、入力欄を空に戻し、利用者一覧を再取得する
        this.registering.set(false);
        this.registerName.set('');
        this.registerEmail.set('');
        this.registerPassword.set('');
        this.loadUsers();
      },
      error: (err) => {
        // 失敗したら登録中状態を解除する
        this.registering.set(false);
        if (err.status === 400 && err.error?.errors) {
          // サーバー側バリデーションエラーは各入力欄のエラー表示に反映する
          this.registerFieldErrors.set(err.error.errors);
        } else {
          // その他の想定外のエラーはまとめてエラーメッセージとして表示する
          this.registerErrorMessage.set(err.error?.message ?? '登録に失敗しました。');
        }
      },
    });
  }

  /** 利用者一覧をAPIから取得し直す（初回表示時・管理者登録成功後の再表示時に呼ばれる）。 */
  private loadUsers(): void {
    // 読み込み中表示に切り替える
    this.loading.set(true);
    // 利用者一覧を取得する
    this.userApi.list().subscribe({
      next: (users) => {
        // 取得した一覧をsignalに反映し、読み込み中表示を終える
        this.users.set(users);
        this.loading.set(false);
      },
      error: (err) => {
        // 取得失敗時はエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? 'ユーザー一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
