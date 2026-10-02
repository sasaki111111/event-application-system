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
/**
 * SC-16 コメントモデレーション画面（/admin/comments）を担当するComponent。
 *
 * - ルーティング定義（app.routes.ts）でこのパスには authGuard・adminGuard が設定されており、
 *   管理者以外のユーザーはアクセスできない（＝管理者専用画面）。
 * - 利用するAngular Service: CommentApiService（全イベント横断でのコメント一覧取得、削除）。
 * - 画面遷移: この画面自体には他画面への遷移リンクは無く、ヘッダー等の共通ナビゲーションから
 *   この画面へ入ってくる想定。削除操作のみ行い、一覧はその場で更新される。
 */
export class AdminCommentList implements OnInit {
  protected readonly comments = signal<CommentModeration[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  // 削除処理中の対象コメントID。nullなら削除中の行は無い（ボタンの「削除中...」表示や多重クリック防止に使う）
  protected readonly deletingId = signal<number | null>(null);

  constructor(private readonly commentApi: CommentApiService) {}

  /**
   * Angularのライフサイクルフックの一つ。Componentが画面に表示される直前に一度だけ実行される。
   * ここではコメント一覧の初回読み込みを行っている。
   */
  ngOnInit(): void {
    this.loadComments();
  }

  /**
   * 各行の「削除」ボタン（(click)="deleteComment(comment)"）から呼ばれる処理。
   * confirm()でブラウザの確認ダイアログを出し、OKされた場合のみAPIを呼んで削除する。
   * AP-21: 投稿者本人または管理者のみ削除できるが、この画面は常に管理者からのアクセスのため実行できる
   */
  protected deleteComment(comment: CommentModeration): void {
    // 確認ダイアログでOKされなければ（キャンセルされたら）ここで処理を中断する
    if (!confirm(`「${comment.eventName}」のコメントを削除しますか？`)) {
      return;
    }
    // どのコメントを削除中かをsignalに記録する（ボタンの表示切り替えに使う）
    this.deletingId.set(comment.id);
    // 削除APIを呼び出す
    this.commentApi.remove(comment.id).subscribe({
      // 成功したら一覧を再取得する
      next: () => this.loadComments(),
      error: (err) => {
        // 失敗したら削除中状態を解除し、エラーをアラートで表示する
        this.deletingId.set(null);
        alert(err.error?.message ?? 'コメントの削除に失敗しました。');
      },
    });
  }

  /** 全イベント横断のコメント一覧をAPIから取得し直す（初回表示時・削除後の再表示時に呼ばれる）。 */
  private loadComments(): void {
    // 読み込み中表示に切り替える
    this.loading.set(true);
    // 削除中だった行の状態をクリアする
    this.deletingId.set(null);
    // 全イベント横断のコメント一覧を取得する
    this.commentApi.listAll().subscribe({
      next: (comments) => {
        // 取得した一覧をsignalに反映し、読み込み中表示を終える
        this.comments.set(comments);
        this.loading.set(false);
      },
      error: (err) => {
        // 取得失敗時はエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? 'コメント一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
