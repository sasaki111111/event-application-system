// 実行環境: ブラウザ側。イベントコメントの1件分を表示する再帰コンポーネント（返信の無制限階層表示）。
// 自分自身を子コメントの描画に再利用することで、何階層の返信でもインデント表示できる。
// APIの呼び出しはこのコンポーネントでは行わず、reply／delete出力イベントを親へバブルさせ、
// 最終的にevent-detail.ts側でAPIを呼んで一覧を再取得する（既存のpostComment/deleteCommentと同じ流れに統一するため）。
import { CommonModule } from '@angular/common';
import { Component, input, output, signal } from '@angular/core';
import { CommentNode } from '../../core/comment-api';

export interface CommentReplyEvent {
  parentCommentId: number;
  body: string;
}

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

  protected readonly replyFormOpen = signal(false);
  protected readonly replyBody = signal('');

  protected toggleReplyForm(): void {
    this.replyFormOpen.update((open) => !open);
    this.replyBody.set('');
  }

  protected onReplyInput(value: string): void {
    this.replyBody.set(value);
  }

  protected submitReply(): void {
    const body = this.replyBody().trim();
    if (!body) {
      return;
    }
    this.reply.emit({ parentCommentId: this.comment().comment.id, body });
    this.replyBody.set('');
    this.replyFormOpen.set(false);
  }

  protected onDelete(): void {
    this.delete.emit(this.comment().comment.id);
  }

  // 子コメント（返信）からのイベントをそのまま上位へ中継する（何階層でも最終的にevent-detail.tsまで届く）
  protected onChildReply(event: CommentReplyEvent): void {
    this.reply.emit(event);
  }

  protected onChildDelete(commentId: number): void {
    this.delete.emit(commentId);
  }
}
