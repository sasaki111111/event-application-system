// 実行環境: ブラウザ側（クライアントサイド）。Angularアプリの一番最初に実行されるファイル。
// `npm start`でビルドされ、ブラウザがindex.htmlを開いたときにこのスクリプトが動く。
//
// [Angularの基礎] bootstrapApplication()は、指定したComponent（ここではApp）を
// アプリケーション全体のルート（一番外側）として起動する関数。index.htmlの<app-root>要素の
// 位置に、このコンポーネントとその配下の画面がレンダリングされる。第2引数のappConfigには、
// ルーティングやHTTP通信などアプリ全体で使う設定（provider）を渡す。
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// ルートコンポーネント(App)をappConfig（ルーティング等の設定）と一緒に画面に描画する
bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
