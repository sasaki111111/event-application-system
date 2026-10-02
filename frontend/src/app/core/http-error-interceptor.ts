// 実行環境: ブラウザ側。全HTTPリクエストのエラーを共通処理する関数型インターセプター（E-1）。
// バックエンドが返すエラー本文（ErrorResponse.message）はそのまま各画面で使えるようにし、
// ここでは「バックエンドに繋がらない」ケース（status 0）だけを分かりやすいメッセージに正規化する。
// 各画面のerrorハンドラーは err.error?.message を読めば、通信エラーもAPIエラーも同じ形で扱える。
// HTTPインターセプターの仕組みそのものの説明はdummy-auth-interceptor.tsを参照。
// HttpErrorResponse: 通信エラー・APIエラーの情報を持つ型。HttpInterceptorFn: 関数型インターセプターの型
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
// catchError: Observableにエラーが流れてきたときだけ処理する演算子。throwError: エラーを流すObservableを作る関数
import { catchError, throwError } from 'rxjs';

const CONNECTION_ERROR_MESSAGE = 'サーバーに接続できません。バックエンドが起動しているか確認してください。';

/**
 * 全HTTPリクエストのレスポンスを監視し、エラー（HttpErrorResponse）を共通処理するインターセプター。
 * app.config.tsのprovideHttpClient(withInterceptors([...]))に登録されており、dummyAuthInterceptorの
 * 後段で全レスポンスを通過する。バックエンド自体に接続できないケース（status 0）だけをここで
 * わかりやすいメッセージに書き換え、それ以外のエラーはそのまま呼び出し元（各Componentのerror
 * ハンドラー）に伝える。
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  // next(req)もHttpInterceptorFnが返すObservable。catchErrorはエラー（throwError）が流れてきた
  // ときだけ処理を差し込むrxjsの演算子で、正常なレスポンスにはなにもしない。
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
