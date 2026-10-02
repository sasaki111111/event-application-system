// 実行環境: ブラウザ側。/admin/**への画面遷移を管理者以外は通さないルートガード（E-7）。
// 画面遷移図「管理者以外が/admin/*にアクセス→イベント一覧」に対応。ルート側でauthGuardの後に適用するため、
// ここに来る時点でログイン済み（currentUserIdはnullでない）であることが前提。
//
// [Angularの基礎: ルートガードとは] 指定した画面（ルート）へ遷移する直前に、Angular Routerが
// 自動的に呼び出す「通行判定」の関数。ここで定義する関数をapp.routes.tsの該当ルートのcanActivateに
// 登録しておくと、その画面を開こうとするたびにこの関数が実行される。戻り値がtrueなら遷移を許可し、
// UrlTree（別ルートへの行き先を表すオブジェクト）を返すと、その行き先へのリダイレクトに差し替えて
// 元の遷移を阻止する（falseを返して単純に遷移を止めることもできる）。
// inject(): DIコンテナ（Angularがサービスのインスタンスを管理する仕組み）から値を取り出す関数
import { inject } from '@angular/core';
// CanActivateFn: ルートガードとして使う関数の型。Router: 画面遷移やUrlTree生成を行うサービス
import { CanActivateFn, Router } from '@angular/router';
// ログイン中ユーザー（userId・role）を保持するストア
import { DummyUserStore } from './dummy-user-store';

/**
 * 管理者専用ルート（/admin/**）用のガード。CanActivateFn型で実装する「関数型ガード」。
 * app.routes.tsの管理者向けルートでcanActivate: [authGuard, adminGuard]として登録されており、
 * authGuardでログイン済みと確認された後に、続けてこの関数がロールを判定する。
 *
 * @returns ログイン中ユーザーが管理者ならtrue（遷移を許可）。管理者でない場合は、
 *          イベント一覧（/events）へ遷移先を差し替えるUrlTreeを返す。
 */
export const adminGuard: CanActivateFn = () => {
  // inject(): コンストラクタを持たない関数（この関数型ガードなど）の中でも、
  // Angularの依存注入（DI）の仕組みを使ってサービスのインスタンスを取り出せる関数。
  // クラスのコンストラクタ注入と同じDIコンテナから取得する。
  const dummyUserStore = inject(DummyUserStore);
  // isAdmin()はcomputed（dummy-user-store.ts）で、現在のroleが"admin"かどうかを返す
  if (dummyUserStore.isAdmin()) {
    // 管理者なので、このまま/admin/**への遷移を許可する
    return true;
  }
  // 管理者でない場合は、遷移先をイベント一覧（/events）に差し替えるUrlTreeを作って返す
  // （Routerをここで改めてinject()しているのは、isAdminがfalseのときだけRouterが必要なため）
  return inject(Router).createUrlTree(['/events']);
};
