// 実行環境: ブラウザ側。すべてのAPIリクエストにダミー認証ヘッダ（X-User-Id）を自動で付ける関数型インターセプター。
// docs/03_API設計書.md 2.1の認証方式（X-User-Idヘッダ）に対応。
// どのuserIdを付けるかはDummyUserStoreが持つ（SC-01ログイン画面で選んだロール、E-2）。
// 未ログイン時はヘッダを付けない（authGuardで保護ルートには来ない想定だが、念のためbackend側の401に委ねる）。
//
// [Angularの基礎: HTTPインターセプターとは] HttpClientが送信するすべてのHTTPリクエスト・
// レスポンスを横断的に加工・監視できる仕組み。app.config.tsのprovideHttpClient(withInterceptors([...]))
// に登録しておくと、画面のコードが呼ぶHttpClient.get()/post()等のすべてに対して、ここで定義する
// 関数が自動的に挟み込まれる。引数のreqが送信前のリクエスト、nextは「次のインターセプター（無ければ
// 実際の通信）へ処理を渡す」関数。このファイルはリクエストを書き換える例、http-error-interceptor.tsは
// レスポンス（エラー）を加工する例になっている。
// HttpInterceptorFn: 関数型インターセプターを実装するための型
import { HttpInterceptorFn } from '@angular/common/http';
// inject(): DIコンテナからサービスを取り出す関数
import { inject } from '@angular/core';
import { DummyUserStore } from './dummy-user-store';

/**
 * ログイン中ユーザーのuserIdをX-User-IdヘッダとしてすべてのHTTPリクエストに付与するインターセプター。
 * app.config.tsのprovideHttpClient(withInterceptors([...]))に登録されており、HttpClientを使う
 * すべてのService（application-api.ts等）のリクエストに対して自動的に働く。
 */
export const dummyAuthInterceptor: HttpInterceptorFn = (req, next) => {
  // ログイン中ユーザーのuserIdを取得する（未ログインならnull）
  const userId = inject(DummyUserStore).currentUserId();
  if (userId === null) {
    // 未ログインの場合はヘッダを付けず、そのままリクエストを次へ渡す
    return next(req);
  }
  // req.clone(): HttpRequestはイミュータブル（作成後に変更できない）オブジェクトのため、
  // ヘッダを追加した「別の」リクエストオブジェクトを複製して作り、それをnext()に渡す。
  const withAuthHeader = req.clone({
    setHeaders: { 'X-User-Id': userId },
  });
  // ヘッダを付けた複製のリクエストを次のインターセプター（または実際の通信）へ渡す
  return next(withAuthHeader);
};
