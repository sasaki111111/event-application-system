// 実行環境: ブラウザ側。すべてのAPI呼び出しのエラーを共通で処理する関数型インターセプター。
// (1) サーバーに接続できない場合（status 0）に、メッセージを統一する。
// (2) 認証エラー（401）の場合に、ログイン状態を解除してログイン画面（SC-010）へ遷移させる。
// docs/20_基本設計/24_方式設計書.md 5.3、docs/30_詳細設計/33_共通詳細設計書.md 5.4。
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { LoginUserStore } from './login-user-store';

const CONNECTION_ERROR_MESSAGE = 'サーバーに接続できません。バックエンドが起動しているか確認してください。';

// ログインAPI（AP-010）の401は「メールアドレスまたはパスワードの誤り」であり、ログイン画面上に
// メッセージを表示する。ログイン状態が無効になったことを表す401とは意味が違うため、自動遷移の対象から除く
const LOGIN_API_PATH = '/api/login';

/**
 * API呼び出しのエラーを共通で処理する。app.config.tsのwithInterceptors()に登録され、
 * すべてのHttpClient呼び出しのレスポンスがここを通る。
 * どの場合も、エラーは呼び出し元（各画面）へそのまま伝える（画面側のエラー処理を妨げない）。
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  // inject()はインターセプター関数の実行開始時（注入コンテキスト内）でのみ呼べるため、先に取得しておく
  const loginUserStore = inject(LoginUserStore);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // status 0 = サーバーに到達できなかった（バックエンド未起動・ネットワーク断等）
      if (error.status === 0) {
        return throwError(
          () =>
            new HttpErrorResponse({
              error: { message: CONNECTION_ERROR_MESSAGE },
              status: error.status,
              statusText: error.statusText,
              url: error.url ?? undefined,
            }),
        );
      }

      // 401 = 利用者を識別できない（退会済み、利用者が存在しない、ログイン情報が無い等）。
      // 画面が保持しているログイン状態はもう使えないため、解除してログイン画面へ案内する
      if (error.status === 401 && !req.url.endsWith(LOGIN_API_PATH)) {
        loginUserStore.logout();
        router.navigateByUrl('/login');
      }

      return throwError(() => error);
    }),
  );
};
