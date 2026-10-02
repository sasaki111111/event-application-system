// 実行環境: ブラウザ側。SC-01ログイン画面（E-2、機能追加でメールアドレス入力方式に変更）。
// 要件定義書: 「簡易ログイン（ダミー認証）。ログイン画面でメールアドレスを入力すると、
// そのユーザーのロールを自動判定してそれぞれの画面へ遷移する」。パスワードは無い。
// 機能追加：軽い会員登録（名前・メールだけで一般ユーザーを作成し、そのままログインする）も同じ画面に持つ。
// `inject()`は、Angularの依存性注入（DI）の仕組みでService等のインスタンスを取得する関数。
// コンストラクタの引数で受け取る書き方（event-detail.ts等を参照）と役割は同じで、
// クラスのフィールド定義の中で直接呼び出せる点が異なる（新しいComponentではこちらが主流）。
// `signal()`はAngularの「状態（画面に表示する値）」を保持する入れ物。値を書き換えると
// 参照しているテンプレートが自動的に再描画される。読み取りは`email()`のように関数呼び出しの形。
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DummyUserStore } from '../core/dummy-user-store';
import { LoginApiService } from '../core/login-api';

// 新規登録フォームの入力エラー1件分（対象フィールド名とエラー文言）
interface FieldError {
  field: string;
  message: string;
}

/**
 * SC-01ログイン画面を担当するComponent。
 * 1つの画面に「ログイン」フォームと「新規登録」フォームの2つを持つ。
 *
 * 使用するAngular Service:
 * - `DummyUserStore`: ログイン中ユーザー（id・ロール）をアプリ全体で共有する状態。
 *   ログイン・登録に成功した際にここへ記録する。
 * - `Router`: ログイン・登録成功後に別の画面へ遷移するために使う。
 * - `LoginApiService`: ログインAPI・会員登録APIの呼び出しをまとめたService。
 *
 * 画面遷移: ログイン成功時はロールに応じて管理者ダッシュボード（/admin/dashboard）か
 * イベント一覧（/events）へ遷移する。新規登録成功時は常にイベント一覧（/events）へ遷移する。
 */
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
  // 新規登録フォームの項目別エラー（サーバー側のバリデーションエラーをそのまま保持する）
  protected readonly registerFieldErrors = signal<FieldError[]>([]);

  /** メールアドレス入力欄（ログイン用）の`(input)`イベントで呼ばれ、入力中の値をsignalに反映する。 */
  protected onEmailInput(value: string): void {
    this.email.set(value);
  }

  /** ログインフォームの送信（submit）で呼ばれる。メールアドレスのみでログインAPIを呼び出す。 */
  protected login(): void {
    // 入力されたメールアドレスから前後の空白を取り除く
    const email = this.email().trim();
    // 未入力ならAPIを呼ばずにエラーメッセージを表示して処理を中断する
    if (!email) {
      this.errorMessage.set('メールアドレスを入力してください。');
      return;
    }

    // これから送信するので、前回表示していたエラーメッセージを消す
    this.errorMessage.set(null);
    // 通信中であることを示すフラグをtrueにする（ボタンの無効化・文言切替に使われる）
    this.loading.set(true);

    // `.subscribe({ next, error })`は、Observable（時間差で届く非同期の結果）を受け取るための書き方。
    // HTTP通信のレスポンスはすぐには返ってこないため、Observableという形で届き、
    // 成功した時は`next`、失敗した時は`error`に渡した処理が実行される。
    this.loginApi.login(email).subscribe({
      next: (user) => {
        // 通信が終わったのでローディング状態を解除する
        this.loading.set(false);
        // ログイン中ユーザー情報（id・ロール）をアプリ全体で共有する状態に記録する
        this.dummyUserStore.login(String(user.userId), user.role);
        // ロールに応じて管理者ダッシュボードかイベント一覧へ画面遷移する
        this.router.navigateByUrl(user.role === 'admin' ? '/admin/dashboard' : '/events');
      },
      error: (err) => {
        // 失敗した場合もローディング状態を解除する
        this.loading.set(false);
        // 401（該当メールアドレスが無い）の場合は専用の文言、それ以外はサーバーからのメッセージか既定の文言を表示する
        this.errorMessage.set(
          err.status === 401 ? 'そのメールアドレスは登録されていません。' : (err.error?.message ?? 'ログインに失敗しました。'),
        );
      },
    });
  }

  /** 新規登録フォームの「名前」入力欄の`(input)`イベントで呼ばれる。 */
  protected onRegisterNameInput(value: string): void {
    this.registerName.set(value);
  }

  /** 新規登録フォームの「メールアドレス」入力欄の`(input)`イベントで呼ばれる。 */
  protected onRegisterEmailInput(value: string): void {
    this.registerEmail.set(value);
  }

  /**
   * 指定したフィールド（例: 'name'、'email'）に対応するエラーメッセージを返す。
   * 無ければnull。テンプレート側で各入力欄の直下にエラー文言を表示するために使う。
   */
  protected registerFieldError(field: string): string | null {
    // registerFieldErrorsの配列から対象フィールドのエラーを探し、見つかればその文言、無ければnullを返す
    return this.registerFieldErrors().find((e) => e.field === field)?.message ?? null;
  }

  // 登録できるのは常に一般ユーザー（パスワードは扱わない軽い登録のため、管理者作成の経路は用意しない）
  /** 新規登録フォームの送信（submit）で呼ばれる。名前・メールアドレスで会員登録APIを呼び出す。 */
  protected register(): void {
    // これから送信するので、前回表示していたエラーメッセージ（全体・項目別）を消す
    this.registerErrorMessage.set(null);
    this.registerFieldErrors.set([]);
    // 通信中であることを示すフラグをtrueにする
    this.registering.set(true);

    // 会員登録APIを呼び出す。成功時はnext、失敗時はerrorの処理が実行される
    this.loginApi.register(this.registerName(), this.registerEmail()).subscribe({
      next: (created) => {
        // 通信が終わったのでローディング状態を解除する
        this.registering.set(false);
        // 作成されたユーザー情報でログイン状態にする
        this.dummyUserStore.login(String(created.userId), created.role);
        // 登録後は常にイベント一覧へ画面遷移する
        this.router.navigateByUrl('/events');
      },
      error: (err) => {
        // 失敗した場合もローディング状態を解除する
        this.registering.set(false);
        // 400（入力エラー）でフィールド別エラーが含まれる場合はそれを表示し、
        // それ以外はサーバーからのメッセージか既定の文言を全体エラーとして表示する
        if (err.status === 400 && err.error?.errors) {
          this.registerFieldErrors.set(err.error.errors);
        } else {
          this.registerErrorMessage.set(err.error?.message ?? '登録に失敗しました。');
        }
      },
    });
  }
}
