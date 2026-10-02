// 実行環境: ブラウザ側。バックエンド（機能追加：イベントコメント、API-20〜22）を呼び出すサービス。
// HTTP通信を行うAngular標準のサービス
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

// API-20・API-21のレスポンス1件分（backendのEventCommentResponseと対応）
export interface EventComment {
  id: number;
  userName: string;
  body: string;
  createdAt: string;
  // ログイン中ユーザー本人の投稿か（削除ボタンの表示可否に使う）
  mine: boolean;
  // （機能追加）: 返信先のコメントID（返信でない場合はNULL）
  parentCommentId: number | null;
  // （機能追加）: 返信が残っているため論理削除されたコメントか。trueの場合bodyは固定の削除済み表示文言になる
  deleted: boolean;
}

// （機能追加）: コメントを親子構造に組み立てた1件分（木構造への組み立てはフロントエンド側で行う）
export interface CommentNode {
  comment: EventComment;
  children: CommentNode[];
}

// AP-31のレスポンス1件分（backendのUserCommentResponseと対応）
export interface UserComment {
  id: number;
  eventId: number;
  eventName: string;
  body: string;
  createdAt: string;
  deleted: boolean;
}

// AP-32のレスポンス1件分（backendのCommentModerationResponseと対応）
export interface CommentModeration {
  id: number;
  eventId: number;
  eventName: string;
  userName: string;
  body: string;
  createdAt: string;
}

// （機能追加）: フラットなコメント配列（parentCommentId付き）から、表示用の木構造を組み立てる。
// 投稿日時昇順（APIの返す順）を各階層でもそのまま維持する
/**
 * @param comments list()で取得したフラットなコメント配列（parentCommentIdで親子関係を表す）
 * @returns 親子関係を組み立てた木構造（ルートコメントの配列。各要素がchildrenに返信を持つ）
 */
export function buildCommentTree(comments: EventComment[]): CommentNode[] {
  // コメントIDをキーに、各コメントを入れ物（CommentNode。children: []は空の返信リストで初期化）に
  // 変換したものをMapに登録する。後工程で「親のノードをid検索で即座に取得できる」ようにするため
  const nodeById = new Map<number, CommentNode>();
  for (const comment of comments) {
    nodeById.set(comment.id, { comment, children: [] });
  }
  // 最終的に返す、親を持たないコメント（ルート）の配列
  const roots: CommentNode[] = [];
  for (const comment of comments) {
    // 1周目で作ったこのコメント自身のノードを取り出す（!は「必ず存在する」ことの表明）
    const node = nodeById.get(comment.id)!;
    // parentCommentIdがあればその親ノードを探す。親が無い（null）場合はundefined
    const parent = comment.parentCommentId !== null ? nodeById.get(comment.parentCommentId) : undefined;
    if (parent) {
      // 親が見つかった＝返信なので、親のchildrenに追加する
      parent.children.push(node);
    } else {
      // 親が無い（または親が見つからない）＝ルートコメントとして配列に追加する
      roots.push(node);
    }
  }
  return roots;
}

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * イベントコメント（投稿・一覧取得・削除）およびコメント管理（件数・モデレーション一覧）に関する
 * バックエンドAPIを呼び出すサービス。イベント詳細画面（コメント投稿・一覧・削除）、マイページ
 * （自分のコメント履歴）、管理者のコメント管理画面から利用される想定。
 */
@Injectable({ providedIn: 'root' })
export class CommentApiService {
  constructor(private readonly http: HttpClient) {}

  /** イベントに投稿されたコメント一覧を取得する（API-20、投稿日時の昇順、フラットな配列）。 */
  list(eventId: number): Observable<EventComment[]> {
    // 指定イベントのコメント一覧をGETで取得する
    return this.http.get<EventComment[]>(`${API_BASE_URL}/events/${eventId}/comments`);
  }

  /** イベントにコメントを投稿する（API-21）。parentCommentIdを指定すると、そのコメントへの返信として投稿する。 */
  post(eventId: number, body: string, parentCommentId?: number): Observable<EventComment> {
    // 本文（body）と返信先ID（parentCommentId、通常の投稿ならundefined）をボディに入れてPOSTする
    return this.http.post<EventComment>(`${API_BASE_URL}/events/${eventId}/comments`, { body, parentCommentId });
  }

  /** コメントを削除する（API-22、投稿者本人または管理者のみ。返信が残っている場合はサーバー側で論理削除される）。 */
  remove(commentId: number): Observable<void> {
    // 対象コメントIDに対してDELETEリクエストを送る
    return this.http.delete<void>(`${API_BASE_URL}/comments/${commentId}`);
  }

  /** コメント総数を取得する（AP-30、管理者専用。論理削除済みは除く）。 */
  count(): Observable<{ count: number }> {
    // { count: number }はこのメソッド専用の使い捨ての型。interfaceを別途定義せずインラインで書いている
    return this.http.get<{ count: number }>(`${API_BASE_URL}/comments/count`);
  }

  /** 全イベント横断の有効なコメント一覧を取得する（AP-32、管理者専用）。 */
  listAll(): Observable<CommentModeration[]> {
    // イベントIDの指定なしで、全イベント分のコメントをまとめて取得する
    return this.http.get<CommentModeration[]>(`${API_BASE_URL}/comments`);
  }
}
