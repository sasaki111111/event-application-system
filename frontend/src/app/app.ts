// 実行環境: ブラウザ側。アプリ全体を包むルートコンポーネント（<app-root>）。
// RouterOutletの場所に、現在のURLに対応する画面が差し込まれる。
//
// [Angularの基礎: @Componentとは] @Componentが付いたクラスは画面の部品（コンポーネント）を表す。
// selectorで指定した名前のHTMLタグ（ここでは<app-root>）がその部品の表示場所になり、
// templateUrlで指定したHTMLファイルが画面の見た目、styleUrlで指定したCSSファイルがその見た目専用の
// スタイルになる。importsには、このテンプレートの中で使う他のコンポーネント・ディレクティブ
// （ここではRouterOutletとRouterLink）を書く。
//
// [Angularの基礎: signal()] 値が変わるとテンプレートの表示を自動的に更新してくれる「状態の箱」。
// 詳しくはcore/login-user-store.tsを参照。
import { Component, signal } from '@angular/core';
import { Router, RouterOutlet, RouterLink } from '@angular/router';
import { LoginUserStore } from './core/login-user-store';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontend');

  // [Angularの基礎: コンストラクタ注入（DI）] コンストラクタの引数にサービスの型を書くだけで、
  // Angularがインスタンス生成時にそのサービスを自動的に渡してくれる。core/配下のGuard・
  // Interceptorで使うinject()関数と仕組みは同じだが、こちらはクラスのコンストラクタでのみ使える
  // 書き方。loginUserStoreをprotectedにしているのは、app.html（テンプレート側）から
  // loginUserStore.currentUserId()等を直接参照するため。
  constructor(
    protected readonly loginUserStore: LoginUserStore,
    private readonly router: Router,
  ) {}

  /** ログアウトしてログイン画面へ遷移する。app.htmlの「ログアウト」ボタンから呼ばれる。 */
  protected logout(): void {
    this.loginUserStore.logout();
    this.router.navigateByUrl('/login');
  }
}
