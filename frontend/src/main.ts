// 実行環境: ブラウザ側（クライアントサイド）。Angularアプリの一番最初に実行されるファイル。
// `npm start`でビルドされ、ブラウザがindex.htmlを開いたときにこのスクリプトが動く。
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// ルートコンポーネント(App)をappConfig（ルーティング等の設定）と一緒に画面に描画する
bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
