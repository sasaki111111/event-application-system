// 実行環境: ブラウザ側。SC-900 該当ページなし画面。未定義URLへのアクセス時に表示する（docs/20_基本設計/21_画面基本設計書.md 4.1）。
// `RouterLink`は、Angularが提供する「画面遷移用のディレクティブ」。<a>タグに付けると、
// 通常のリンクのようにページ全体を再読み込みせずに、Angular内のルーティングだけで別画面へ移動できる。
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * SC-900 該当ページなし（404、Not Found）を表示するだけのComponent。
 * 他のComponentと違い、APIを呼ぶ処理や状態（signal）は一切持たない。
 * `@Component`デコレータは、このクラスをAngularの「画面の部品（Component）」として
 * 登録するための印。`imports`に書かれたものだけが、対応するHTMLテンプレート
 * （not-found.html）の中で使えるようになる。
 *
 * 画面遷移: 存在しないURLにアクセスした時にルーティング設定（app.routes.tsのワイルドカード'**'）経由で
 * 表示される。画面内の「イベント一覧に戻る」リンク（routerLink="/events"）から
 * イベント一覧画面（SC-020）へ戻れる。
 */
@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  templateUrl: './not-found.html',
  styleUrl: './not-found.css',
})
export class NotFound {}
