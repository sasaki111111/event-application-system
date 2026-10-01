// 実行環境: ブラウザ側。SC-16 コメントモデレーション画面（/admin/comments）。
// 全イベント横断で有効な（論理削除されていない）コメントを確認し、削除できる管理者専用画面。
// イベント別・投稿者別の絞り込みは提供しないが、各行にイベント名・投稿者名を表示し、
// どのイベント・誰の投稿かが常にわかるようにする。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { CommentApiService, CommentModeration } from '../../core/comment-api';

@Component({
  selector: 'app-admin-comment-list',
  imports: [CommonModule],
  templateUrl: './admin-comment-list.html',
  styleUrl: './admin-comment-list.css',
})
export class AdminCommentList implements OnInit {
  protected readonly comments = signal<CommentModeration[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly deletingId = signal<number | null>(null);

  constructor(private readonly commentApi: CommentApiService) {}

  ngOnInit(): void {
    this.loadComments();
  }

  // AP-21: 投稿者本人または管理者のみ削除できるが、この画面は常に管理者からのアクセスのため実行できる
  protected deleteComment(comment: CommentModeration): void {
    if (!confirm(`「${comment.eventName}」のコメントを削除しますか？`)) {
      return;
    }
    this.deletingId.set(comment.id);
    this.commentApi.remove(comment.id).subscribe({
      next: () => this.loadComments(),
      error: (err) => {
        this.deletingId.set(null);
        alert(err.error?.message ?? 'コメントの削除に失敗しました。');
      },
    });
  }

  private loadComments(): void {
    this.loading.set(true);
    this.deletingId.set(null);
    this.commentApi.listAll().subscribe({
      next: (comments) => {
        this.comments.set(comments);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? 'コメント一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
