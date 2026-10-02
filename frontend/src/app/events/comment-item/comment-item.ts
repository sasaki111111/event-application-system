// 実行環境: ブラウザ側。イベントコメントの1件分を表示する再帰コンポーネント（返信の無制限階層表示）。
// 自分自身を子コメントの描画に再利用することで、何階層の返信でもインデント表示できる。
// APIの呼び出しはこのコンポーネントでは行わず、reply／delete出力イベントを親へバブルさせ、
// 最終的にevent-detail.ts側でAPIを呼んで一覧を再取得する（既存のpostComment/deleteCommentと同じ流れに統一するため）。
// `input()`／`output()`は、親Component⇔子Component間でデータをやり取りするためのAngularの機能。
// `input()`は親から渡される値（プロパティ）を受け取る入口、`output()`は子から親へイベントを
// 発信する出口。従来の`@Input()`/`@Output()`デコレータの代わりに使える新しい書き方。
import { CommonModule } from '@angular/common';
import { Component, input, output, signal } from '@angular/core';
import { CommentNode } from '../../core/comment-api';

// 返信イベント1件分（どのコメントへの返信か・本文）。reply出力の型として使う
export interface CommentReplyEvent {
  parentCommentId: number;
  body: string;
}

/**
 * コメント1件（とその返信）を表示する再帰Component。event-detail.tsのコメント一覧から、
 * また自分自身の子コメント描画からも繰り返し使われる。
 * このComponent自体はAPIを呼ばず、reply／delete出力（output）を親へ伝えるだけで、
 * 実際のAPI呼び出しと再取得はevent-detail.ts側が行う。
 *
 * 入力（input）:
 * - `comment`: 表示するコメント1件分のデータ（子コメントの配列を含む）。
 * - `isAdmin`: ログイン中ユーザーが管理者かどうか（削除ボタンの表示判定に使う）。
 *
 * 出力（output）:
 * - `reply`: 返信フォームで投稿された時に発行する（親コメントID＋本文）。
 * - `delete`: 削除ボタンが押された時に発行する（コメントID）。
 */
@Component({
  selector: 'app-comment-item',
  imports: [CommonModule, CommentItem],
  templateUrl: './comment-item.html',
  styleUrl: './comment-item.css',
})
export class CommentItem {
  readonly comment = input.required<CommentNode>();
  readonly isAdmin = input(false);

  readonly reply = output<CommentReplyEvent>();
  readonly delete = output<number>();

  // 返信フォームを開いているかどうか
  protected readonly replyFormOpen = signal(false);
  protected readonly replyBody = signal('');

  /** 「返信」ボタン（(click)）で呼ばれる。返信フォームの表示／非表示を切り替え、入力中の内容をリセットする。 */
  protected toggleReplyForm(): void {
    this.replyFormOpen.update((open) => !open);
    this.replyBody.set('');
  }

  /** 返信フォームのテキスト欄の`(input)`イベントで呼ばれる。 */
  protected onReplyInput(value: string): void {
    this.replyBody.set(value);
  }

  /** 「返信を投稿」ボタン（(click)）で呼ばれる。空文字でなければreplyイベントを親へ発行し、フォームを閉じる。 */
  protected submitReply(): void {
    const body = this.replyBody().trim();
    if (!body) {
      return;
    }
    this.reply.emit({ parentCommentId: this.comment().comment.id, body });
    this.replyBody.set('');
    this.replyFormOpen.set(false);
  }

  /** 「削除」ボタン（(click)）で呼ばれる。このコメントのIDをdeleteイベントとして親へ発行する。 */
  protected onDelete(): void {
    this.delete.emit(this.comment().comment.id);
  }

  // 子コメント（返信）からのイベントをそのまま上位へ中継する（何階層でも最終的にevent-detail.tsまで届く）
  /** 子の`app-comment-item`（返信）が発行したreplyイベントを受け取り、さらに自分の親へ中継する。 */
  protected onChildReply(event: CommentReplyEvent): void {
    this.reply.emit(event);
  }

  /** 子の`app-comment-item`（返信）が発行したdeleteイベントを受け取り、さらに自分の親へ中継する。 */
  protected onChildDelete(commentId: number): void {
    this.delete.emit(commentId);
  }
}
