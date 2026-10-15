// 実行環境: ブラウザ側。未定義URLへのアクセス時に表示する画面（画面遷移図§3「全画面 | 未定義URLにアクセス | 404」に対応、機能追加）。
// `RouterLink`は、Angularが提供する「画面遷移用のディレクティブ」。<a>タグに付けると、
// 通常のリンクのようにページ全体を再読み込みせずに、Angular内のルーティングだけで別画面へ移動できる。
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * 404画面（Not Found）を表示するだけのComponent。
 * 他のComponentと違い、APIを呼ぶ処理や状態（signal）は一切持たない。
 * `@Component`デコレータは、このクラスをAngularの「画面の部品（Component）」として
 * 登録するための印。`imports`に書かれたものだけが、対応するHTMLテンプレート
 * （not-found.html）の中で使えるようになる。
 *
 * 画面遷移: 存在しないURLにアクセスした時にルーティング設定（app.routes.ts等）経由で
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
