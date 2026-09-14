// 実行環境: ブラウザ側。アプリ全体で使う共通設定（プロバイダー）をまとめる場所。
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
    provideHttpClient(withInterceptors([dummyAuthInterceptor, httpErrorInterceptor])),
  ]
};
