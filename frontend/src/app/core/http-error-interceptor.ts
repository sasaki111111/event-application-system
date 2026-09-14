// 実行環境: ブラウザ側。全HTTPリクエストのエラーを共通処理する関数型インターセプター（E-1）。
// バックエンドが返すエラー本文（ErrorResponse.message）はそのまま各画面で使えるようにし、
// ここでは「バックエンドに繋がらない」ケース（status 0）だけを分かりやすいメッセージに正規化する。
// 各画面のerrorハンドラーは err.error?.message を読めば、通信エラーもAPIエラーも同じ形で扱える。
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

const CONNECTION_ERROR_MESSAGE = 'サーバーに接続できません。バックエンドが起動しているか確認してください。';

export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
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
      return throwError(() => error);
    }),
  );
};
