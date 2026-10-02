// 実行環境: ブラウザ側。バックエンド（イベント申込、自分の申込一覧、申込キャンセル）を呼び出すサービス。
// backendのApplicationController・各Responseと対応。
//
// [Angularの基礎: Serviceとは] 「@Injectable」が付いたクラスはAngularのサービスと呼ばれる、
// 特定の画面に属さない共通処理（この例ではバックエンドAPI呼び出し）をまとめる置き場所。
// ここで定義したクラスは、画面（Component）のコンストラクタやinject()で受け取って使う。
// 同じ画面から複数回使っても、Angularが内部でインスタンスを1つだけ作って共有してくれる
// （providedIn: 'root'の場合、アプリ全体で単一のインスタンス＝シングルトン）。
//
// [Angularの基礎: HttpClientとObservable] HttpClientはHTTP通信（GET/POST/PUT/DELETE等）を
// 行うAngular標準の仕組み。各メソッドはレスポンスをすぐには返さず、Observable（rxjsの型。
// 「将来、値が届く・エラーになるかもしれない」ことを表す箱）を返す。呼び出し側（Component）が
// .subscribe()を呼んだ時点で実際の通信が始まり、成功時はnextコールバック、失敗時はerrorコール
// バックに結果が渡る。subscribeするまで何も起きない点がPromiseと異なる。
// HttpClient: HTTP通信（GET/POST/PUT/DELETE）を行うAngular標準のサービス
import { HttpClient } from '@angular/common/http';
// Injectable: このクラスをAngularのDIコンテナに登録できるサービスにするデコレーター
import { Injectable } from '@angular/core';
// Observable: 非同期の結果（将来届く値・エラー）を表すrxjsの型
import { Observable } from 'rxjs';

// interface: TypeScriptで「このオブジェクトはどんなプロパティを持つべきか」を表す型定義。
// 実行時には何も生成されない（コンパイル時の型チェックのためだけに存在する）。
// ここではバックエンドのJSONレスポンス／リクエストボディの形をそのままTypeScriptの型にしている。
export interface ApplicationResponse {
  id: number;
  eventId: number;
  // 機能追加（定員区分）: 区分の無いイベントへの申込はNULL
  ticketTypeId: number | null;
  userId: number;
  status: string;
  appliedAt: string;
}

// API-04のレスポンス1件分（backendのMyApplicationResponseと対応）
export interface MyApplication {
  id: number;
  eventId: number;
  eventName: string;
  startAt: string;
  status: string;
  appliedAt: string;
  // 機能追加（定員区分）: キャンセル待ちの順位（1始まり）。キャンセル待ち以外はNULL
  waitlistRank: number | null;
}

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * イベント申込に関するバックエンドAPI（申込・自分の申込一覧取得・申込キャンセル）を呼び出すサービス。
 * イベント詳細画面（申込ボタン）・マイページ（申込一覧・キャンセル）から利用される想定。
 */
@Injectable({ providedIn: 'root' })
export class ApplicationApiService {
  // constructor DI: コンストラクタの引数にサービスの型を書いておくだけで、Angularが生成時に
  // 自動でインスタンスを渡してくれる（依存注入／DI）。inject()関数を使う書き方との違いは、
  // こちらはクラスの先頭でまとめて依存関係を宣言できる点（クラスでしか使えない）。
  constructor(private readonly http: HttpClient) {}

  /**
   * イベントに申し込む（API-03）。一般利用者以上が対象で、本人のuserIdに紐付けられる
   * （userIdはHTTPヘッダ経由でバックエンドが自動判定するため引数には含めない）。
   * ticketTypeId・extraAnswerは、定員区分やアンケートがあるイベントの場合のみ指定する（機能追加）。
   */
  apply(eventId: number, ticketTypeId?: number, extraAnswer?: string): Observable<ApplicationResponse> {
    // HttpClient.post(): 第1引数がリクエスト先URL、第2引数がリクエストボディ（JSONになる）。
    // 戻り値のObservableはここではまだ通信していない。呼び出し元が.subscribe()した時点で送信される
    return this.http.post<ApplicationResponse>(`${API_BASE_URL}/applications`, {
      eventId,
      ticketTypeId,
      extraAnswer,
    });
  }

  /** ログイン中ユーザー本人の申込一覧を取得する（API-04、申込日時の降順）。マイページで使用。 */
  myApplications(): Observable<MyApplication[]> {
    // HttpClient.get(): 指定URLにGETリクエストを送るObservableを返す
    return this.http.get<MyApplication[]>(`${API_BASE_URL}/my/applications`);
  }

  /** 本人の申込をキャンセルする（API-05）。 */
  cancel(applicationId: number): Observable<void> {
    // HttpClient.delete(): 指定URLにDELETEリクエストを送るObservableを返す
    return this.http.delete<void>(`${API_BASE_URL}/applications/${applicationId}`);
  }
}
