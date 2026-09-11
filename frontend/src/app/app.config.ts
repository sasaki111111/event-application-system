// 実行環境: ブラウザ側。アプリ全体で使う共通設定（プロバイダー）をまとめる場所。
// E-1で、バックエンドAPIを呼ぶHttpClientの設定などをここに追加していく想定。
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes) // app.routes.tsのルート設定を使ってAngular Routerを有効化する
  ]
};
