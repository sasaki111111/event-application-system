// 実行環境: ブラウザ側。SC-03のマイページ（D-4: 一覧表示、D-5: キャンセル操作）。
// 機能追加: キャンセル待ちの順位表示、お気に入りタブ。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApplicationApiService, MyApplication } from '../core/application-api';
import { FavoriteApiService, FavoriteEvent } from '../core/favorite-api';

@Component({
  selector: 'app-my-applications',
  imports: [CommonModule, RouterLink],
  templateUrl: './my-applications.html',
  styleUrl: './my-applications.css',
})
export class MyApplications implements OnInit {
  protected readonly applications = signal<MyApplication[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly cancellingId = signal<number | null>(null);

  // 機能追加（お気に入りタブ）
  protected readonly activeTab = signal<'applications' | 'favorites'>('applications');
  protected readonly favorites = signal<FavoriteEvent[]>([]);
  protected readonly favoritesLoading = signal(true);
  protected readonly favoritesErrorMessage = signal<string | null>(null);
  protected readonly unfavoritingId = signal<number | null>(null);

  constructor(
    private readonly applicationApi: ApplicationApiService,
    private readonly favoriteApi: FavoriteApiService,
  ) {}

  ngOnInit(): void {
    this.loadApplications();
    this.loadFavorites();
  }

  protected setTab(tab: 'applications' | 'favorites'): void {
    this.activeTab.set(tab);
  }

  // API-16: 未登録でもエラーにしない（冪等）。マイページからの解除は常に登録済みのものだけが対象
  protected unfavorite(favorite: FavoriteEvent): void {
    this.unfavoritingId.set(favorite.id);
    this.favoriteApi.remove(favorite.id).subscribe({
      next: () => this.loadFavorites(),
      error: (err) => {
        this.unfavoritingId.set(null);
        alert(err.error?.message ?? 'お気に入りの解除に失敗しました。');
      },
    });
  }

  private loadFavorites(): void {
    this.favoritesLoading.set(true);
    this.unfavoritingId.set(null);
    this.favoriteApi.myFavorites().subscribe({
      next: (favorites) => {
        this.favorites.set(favorites);
        this.favoritesLoading.set(false);
      },
      error: (err) => {
        this.favoritesErrorMessage.set(err.error?.message ?? 'お気に入り一覧の取得に失敗しました。');
        this.favoritesLoading.set(false);
      },
    });
  }

  // API-05: すでにキャンセル済／開催日時経過は400（要件定義書§8「取消可否チェック」）
  protected cancel(application: MyApplication): void {
    if (!confirm(`「${application.eventName}」への申込をキャンセルしますか？`)) {
      return;
    }

    this.cancellingId.set(application.id);
    this.applicationApi.cancel(application.id).subscribe({
      next: () => this.loadApplications(),
      error: (err) => {
        this.cancellingId.set(null);
        alert(err.error?.message ?? 'キャンセルに失敗しました。');
      },
    });
  }

  private loadApplications(): void {
    this.loading.set(true);
    this.cancellingId.set(null);
    this.applicationApi.myApplications().subscribe({
      next: (applications) => {
        this.applications.set(applications);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? '申込一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
