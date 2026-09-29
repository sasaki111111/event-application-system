// 実行環境: ブラウザ側。SC-15 利用者詳細画面（D-13、/admin/users/:id）。
// 管理者が特定の利用者の基本情報・申込一覧・お気に入り一覧を確認するための閲覧専用画面。
// キャンセル・お気に入り解除等、対象利用者に代わる操作はできない（利用者本人向けのSC-06とは役割が異なる）。
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
  // D-21（機能追加）: コメント履歴。論理削除済み（D-18）のコメントも含む
  protected readonly comments = signal<UserComment[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly userApi: UserApiService,
  ) {}

  ngOnInit(): void {
    const userId = Number(this.route.snapshot.paramMap.get('id'));

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
}
