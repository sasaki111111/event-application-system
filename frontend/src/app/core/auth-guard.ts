// 実行環境: ブラウザ側。未ログインで保護ルートに来た場合に/loginへ戻すルートガード
// （docs/30_詳細設計/33_共通詳細設計書.md 5.4「未ログインで、ログインが必要な画面を開いた」）。
// ルートガード（CanActivateFn）の仕組みそのものの説明はadmin-guard.tsを参照。
// inject(): DIコンテナからサービスのインスタンスを取り出す関数
import { inject } from '@angular/core';
// CanActivateFn: ルートガードの型。Router: UrlTree生成・画面遷移を行うサービス
import { CanActivateFn, Router } from '@angular/router';
// LoginUserStore: ログイン中ユーザーの状態（userId・利用者区分コード）を保持するストア
import { LoginUserStore } from './login-user-store';

/**
 * ログイン必須ルート用のガード。app.routes.tsのログイン画面・該当ページなし以外の全ルートでcanActivateの先頭に登録されており、
 * adminGuardより先に実行される（ログイン確認→管理者確認の順）。
 *
 * @returns ログイン済み（currentUserIdがnullでない）ならtrue。未ログインならログイン画面
 *          （/login）へ遷移先を差し替えるUrlTreeを返す。
 */
export const authGuard: CanActivateFn = () => {
  const loginUserStore = inject(LoginUserStore);
  // currentUserIdがnull以外＝ログイン済みなので、遷移を許可する
  if (loginUserStore.currentUserId() !== null) {
    return true;
  }
  // 未ログインの場合は、遷移先をログイン画面（/login）に差し替えるUrlTreeを返す
  return inject(Router).createUrlTree(['/login']);
};
