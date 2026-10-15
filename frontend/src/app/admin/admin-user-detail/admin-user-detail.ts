// 実行環境: ブラウザ側。SC-141 利用者詳細画面（/admin/users/:id）。
// 管理者が特定の利用者の基本情報・申込一覧・お気に入り一覧を確認するための画面。
// キャンセル・お気に入り解除等、対象利用者に代わる操作はできない（利用者本人向けのSC-030とは役割が異なる）。
// 管理者権限の降格（AP-146）・利用者の匿名化（AP-013）のみ、この画面から実行できる例外的な操作とする。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MyApplication } from '../../core/application-api';
import { FavoriteEvent } from '../../core/favorite-api';
import { UserComment } from '../../core/comment-api';
import { UserApiService, UserSummary } from '../../core/user-api';
import { STATUS_CODE, ROLE_CODE } from '../../core/codes';

@Component({
  selector: 'app-admin-user-detail',
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-user-detail.html',
  styleUrl: './admin-user-detail.css',
})
/**
 * SC-141 利用者詳細画面（/admin/users/:id）を担当するComponent。
 * 特定の利用者の基本情報・申込一覧・お気に入り一覧・コメント履歴を確認する画面。
 *
 * - ルーティング定義（app.routes.ts）でこのパスには authGuard・adminGuard が設定されており、
 *   管理者以外のユーザーはアクセスできない（＝管理者専用画面）。
 * - 利用するAngular Service: UserApiService（利用者本体・申込・お気に入り・コメント履歴の取得、
 *   管理者権限の降格、利用者の匿名化（退会処理））。
 * - 画面遷移: 「← 利用者管理に戻る」（RouterLink）で /admin/users（AdminUserList）へ戻る。
 *   この画面へは利用者一覧画面の各行から遷移してくる想定。
 * - 対象利用者に代わるキャンセル・お気に入り解除等の操作はできない（利用者本人向けの画面とは役割が異なる）。
 *   管理者権限の降格・利用者の匿名化のみ、この画面から実行できる例外的な操作。
 */
export class AdminUserDetail implements OnInit {
  // テンプレートで申込状況コードを判定するために公開する（文字列の表示名では判定しない）
  protected readonly StatusCode = STATUS_CODE;
  // テンプレートで利用者区分コードを判定するために公開する
  protected readonly RoleCode = ROLE_CODE;
  protected readonly user = signal<UserSummary | null>(null);
  protected readonly applications = signal<MyApplication[]>([]);
  protected readonly favorites = signal<FavoriteEvent[]>([]);
  // （機能追加）: コメント履歴。論理削除済みのコメントも含む
  protected readonly comments = signal<UserComment[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly demoting = signal(false);
  protected readonly anonymizing = signal(false);

  private userId = 0;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly userApi: UserApiService,
  ) {}

  /**
   * Angularのライフサイクルフックの一つ。Componentが画面に表示される直前に一度だけ実行される。
   * URLからユーザーIDを取得し、forkJoin（複数のAPI呼び出しを並行実行し、全て完了したら
   * まとめて結果を受け取るRxJSの仕組み）で利用者本体・申込・お気に入り・コメント履歴を同時に取得する。
   */
  ngOnInit(): void {
    // URLの:id部分を数値に変換し、以降の処理で使うユーザーIDとして保持する
    this.userId = Number(this.route.snapshot.paramMap.get('id'));
    const userId = this.userId;

    // 利用者本体・申込一覧・お気に入り一覧・コメント履歴の4つのAPIを並行実行し、
    // 全て完了したタイミングでまとめて結果を受け取る
    forkJoin({
      user: this.userApi.getById(userId),
      applications: this.userApi.applicationsOf(userId),
      favorites: this.userApi.favoritesOf(userId),
      comments: this.userApi.commentsOf(userId),
    }).subscribe({
      next: ({ user, applications, favorites, comments }) => {
        // 取得できた4つの結果をそれぞれ対応するsignalに反映する
        this.user.set(user);
        this.applications.set(applications);
        this.favorites.set(favorites);
        this.comments.set(comments);
        // 読み込み中表示を終える
        this.loading.set(false);
      },
      error: (err) => {
        // いずれか1つでも失敗したらエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? '利用者情報の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }

  /**
   * 「管理者権限を外す」ボタン（(click)="demote()"）から呼ばれる処理。
   * AP-146: 対象が管理者の場合のみ呼び出せる（テンプレート側でボタンの表示を制御する）
   */
  protected demote(): void {
    // 確認ダイアログでキャンセルされたらここで処理を中断する
    if (!confirm('この利用者の管理者権限を外しますか？')) {
      return;
    }
    // 処理中状態にする
    this.demoting.set(true);
    // 管理者権限の降格APIを呼び出す
    this.userApi.demote(this.userId).subscribe({
      next: (user) => {
        // 降格後の最新の利用者情報で画面を更新し、処理中状態を解除する
        this.user.set(user);
        this.demoting.set(false);
      },
      error: (err) => {
        // 失敗したら処理中状態を解除し、エラーをアラートで表示する
        this.demoting.set(false);
        alert(err.error?.message ?? '管理者権限の降格に失敗しました。');
      },
    });
  }

  /**
   * 「退会させる」ボタン（(click)="anonymize()"）から呼ばれる処理。
   * AP-013: 対象が管理者、または既に退会済みの場合は呼び出せない（テンプレート側でボタンの表示を制御する）
   */
  protected anonymize(): void {
    // 確認ダイアログでキャンセルされたらここで処理を中断する
    if (!confirm('この利用者を退会させますか？この操作は取り消せません。')) {
      return;
    }
    // 処理中状態にする
    this.anonymizing.set(true);
    // 匿名化（退会処理）APIを呼び出す
    this.userApi.anonymize(this.userId).subscribe({
      next: (user) => {
        // 退会処理後の最新の利用者情報で画面を更新し、処理中状態を解除する
        this.user.set(user);
        this.anonymizing.set(false);
      },
      error: (err) => {
        // 失敗したら処理中状態を解除し、エラーをアラートで表示する
        this.anonymizing.set(false);
        alert(err.error?.message ?? '利用者の退会処理に失敗しました。');
      },
    });
  }
}
