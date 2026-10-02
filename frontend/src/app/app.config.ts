// 実行環境: ブラウザ側。アプリ全体で使う共通設定（プロバイダー）をまとめる場所。
//
// [Angularの基礎: ApplicationConfigとprovide*関数] ApplicationConfigは、main.tsの
// bootstrapApplication()に渡す「アプリ全体の設定」の型。providers配列に、各provide*関数の
// 戻り値を並べることで、アプリ全体でその機能（ルーティング・HTTP通信等）を使えるようにする。
// ここに登録したサービス・設定は、どのComponent・Serviceからもinject()やコンストラクタ注入で
// 取得できるようになる。
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { dummyAuthInterceptor } from './core/dummy-auth-interceptor';
import { httpErrorInterceptor } from './core/http-error-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes), // app.routes.tsのルート設定を使ってAngular Routerを有効化する
    // バックエンドAPIを呼ぶHttpClient。dummyAuthInterceptorで全リクエストにX-User-Idを自動付与し、
    // httpErrorInterceptorで通信エラー（サーバー未起動等）のメッセージを共通化する（E-1）
    // withInterceptors()に渡した配列の順序どおりに、リクエスト送信前はこの順で、
    // レスポンス受信時は逆順で各インターセプターを通過する
    provideHttpClient(withInterceptors([dummyAuthInterceptor, httpErrorInterceptor])),
  ]
};
