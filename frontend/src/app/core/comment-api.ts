// 実行環境: ブラウザ側。バックエンド（機能追加：イベントコメント、API-20〜22）を呼び出すサービス。
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
  // D-18（機能追加）: 返信先のコメントID（返信でない場合はNULL）
  parentCommentId: number | null;
  // D-18（機能追加）: 返信が残っているため論理削除されたコメントか。trueの場合bodyは固定の削除済み表示文言になる
  deleted: boolean;
}

// D-18（機能追加）: コメントを親子構造に組み立てた1件分（木構造への組み立てはフロントエンド側で行う）
export interface CommentNode {
  comment: EventComment;
  children: CommentNode[];
}

// AP-31のレスポンス1件分（backendのUserCommentResponseと対応、D-21）
export interface UserComment {
  id: number;
  eventId: number;
  eventName: string;
  body: string;
  createdAt: string;
  deleted: boolean;
}

// AP-32のレスポンス1件分（backendのCommentModerationResponseと対応、D-22）
export interface CommentModeration {
  id: number;
  eventId: number;
  eventName: string;
  userName: string;
  body: string;
  createdAt: string;
}

// D-18（機能追加）: フラットなコメント配列（parentCommentId付き）から、表示用の木構造を組み立てる。
// 投稿日時昇順（APIの返す順）を各階層でもそのまま維持する
export function buildCommentTree(comments: EventComment[]): CommentNode[] {
  const nodeById = new Map<number, CommentNode>();
  for (const comment of comments) {
    nodeById.set(comment.id, { comment, children: [] });
  }
  const roots: CommentNode[] = [];
  for (const comment of comments) {
    const node = nodeById.get(comment.id)!;
    const parent = comment.parentCommentId !== null ? nodeById.get(comment.parentCommentId) : undefined;
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class CommentApiService {
  constructor(private readonly http: HttpClient) {}

  // API-20（投稿日時の昇順、フラットな配列。parentCommentIdでの木構造組み立てはフロント側）
  list(eventId: number): Observable<EventComment[]> {
    return this.http.get<EventComment[]>(`${API_BASE_URL}/events/${eventId}/comments`);
  }

  // API-21。parentCommentIdを指定すると、そのコメントへの返信として投稿する（D-18）
  post(eventId: number, body: string, parentCommentId?: number): Observable<EventComment> {
    return this.http.post<EventComment>(`${API_BASE_URL}/events/${eventId}/comments`, { body, parentCommentId });
  }

  // API-22（投稿者本人または管理者のみ。返信が残っている場合はサーバー側で論理削除される、D-18）
  remove(commentId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/comments/${commentId}`);
  }

  // AP-30（D-20）: 管理者専用。コメント総数（論理削除済みは除く）
  count(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${API_BASE_URL}/comments/count`);
  }

  // AP-32（D-22）: 管理者専用。全イベント横断の有効なコメント一覧
  listAll(): Observable<CommentModeration[]> {
    return this.http.get<CommentModeration[]>(`${API_BASE_URL}/comments`);
  }
}
