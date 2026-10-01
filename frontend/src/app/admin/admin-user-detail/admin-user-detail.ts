// 実行環境: ブラウザ側。SC-15 利用者詳細画面（/admin/users/:id）。
// 管理者が特定の利用者の基本情報・申込一覧・お気に入り一覧を確認するための画面。
// キャンセル・お気に入り解除等、対象利用者に代わる操作はできない（利用者本人向けのSC-06とは役割が異なる）。
// 管理者権限の降格（AP-33）・利用者の匿名化（AP-34）のみ、この画面から実行できる例外的な操作とする。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MyApplication } from '../../core/application-api';
import { FavoriteEvent } from '../../core/favorite-api';
import { UserComment } from '../../core/comment-api';
import { UserApiService, UserSummary } from '../../core/user-api';

@Component({
  selector: 'app-admin-user-detail',
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-user-detail.html',
  styleUrl: './admin-user-detail.css',
})
export class AdminUserDetail implements OnInit {
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

  ngOnInit(): void {
    this.userId = Number(this.route.snapshot.paramMap.get('id'));
    const userId = this.userId;

    forkJoin({
      user: this.userApi.getById(userId),
      applications: this.userApi.applicationsOf(userId),
      favorites: this.userApi.favoritesOf(userId),
      comments: this.userApi.commentsOf(userId),
    }).subscribe({
      next: ({ user, applications, favorites, comments }) => {
        this.user.set(user);
        this.applications.set(applications);
        this.favorites.set(favorites);
        this.comments.set(comments);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? '利用者情報の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }

  // AP-33: 対象が管理者の場合のみ呼び出せる（テンプレート側でボタンの表示を制御する）
  protected demote(): void {
    if (!confirm('この利用者の管理者権限を外しますか？')) {
      return;
    }
    this.demoting.set(true);
    this.userApi.demote(this.userId).subscribe({
      next: (user) => {
        this.user.set(user);
        this.demoting.set(false);
      },
      error: (err) => {
        this.demoting.set(false);
        alert(err.error?.message ?? '管理者権限の降格に失敗しました。');
      },
    });
  }

  // AP-34: 対象が管理者、または既に退会済みの場合は呼び出せない（テンプレート側でボタンの表示を制御する）
  protected anonymize(): void {
    if (!confirm('この利用者を退会させますか？この操作は取り消せません。')) {
      return;
    }
    this.anonymizing.set(true);
    this.userApi.anonymize(this.userId).subscribe({
      next: (user) => {
        this.user.set(user);
        this.anonymizing.set(false);
      },
      error: (err) => {
        this.anonymizing.set(false);
        alert(err.error?.message ?? '利用者の退会処理に失敗しました。');
      },
    });
  }
}
