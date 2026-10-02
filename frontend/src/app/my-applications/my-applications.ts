// 実行環境: ブラウザ側。SC-03のマイページ（一覧表示、キャンセル操作）。
// 機能追加: キャンセル待ちの順位表示、お気に入りタブ。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApplicationApiService, MyApplication } from '../core/application-api';
import { DummyUserStore } from '../core/dummy-user-store';
import { FavoriteApiService, FavoriteEvent } from '../core/favorite-api';
import { FavoriteStore } from '../core/favorite-store';
import { UserApiService } from '../core/user-api';

/**
 * SC-03マイページを担当するComponent。「申込一覧」「お気に入り」の2タブを持ち、
 * 申込のキャンセルやお気に入りの解除、アカウントの退会（匿名化）もここから行う。
 *
 * 使用するAngular Service:
 * - `ApplicationApiService`: 自分の申込一覧取得（API）とキャンセルAPI（API-05）の呼び出し。
 * - `FavoriteApiService`: 自分のお気に入り一覧取得とお気に入り解除APIの呼び出し。
 * - `FavoriteStore`: イベント一覧・詳細画面と共有するお気に入り状態。このタブで解除した際に
 *   キャッシュを無効化し、他画面で最新状態を取り直させるために使う。
 * - `UserApiService`: 退会（匿名化）API（AP-34）の呼び出し。
 * - `DummyUserStore`: 退会対象（自分）のユーザーIDの取得、退会後のログアウトに使う。
 * - `Router`: 退会成功後にログイン画面へ遷移するために使う。
 *
 * 画面遷移: 申込一覧・お気に入り一覧のカードのタイトルからevent-detail.ts（イベント詳細）へ
 * 遷移する。退会成功時はログイン画面（/login）へ遷移する。
 */
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

  // AP-34（機能追加）: 退会（匿名化）処理中フラグ
  protected readonly withdrawing = signal(false);

  constructor(
    private readonly applicationApi: ApplicationApiService,
    private readonly favoriteApi: FavoriteApiService,
    private readonly favoriteStore: FavoriteStore,
    private readonly userApi: UserApiService,
    private readonly dummyUserStore: DummyUserStore,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.loadApplications();
    this.loadFavorites();
  }

  protected setTab(tab: 'applications' | 'favorites'): void {
    this.activeTab.set(tab);
  }

  // API-16: 未登録でもエラーにしない（冪等）。マイページからの解除は常に登録済みのものだけが対象。
  // このタブはFavoriteStoreを介さず直接APIで一覧を取得しているため、解除後はストアのキャッシュを
  // 破棄し、event-list・event-detailに戻った時に最新状態を取り直させる
  protected unfavorite(favorite: FavoriteEvent): void {
    this.unfavoritingId.set(favorite.id);
    this.favoriteApi.remove(favorite.id).subscribe({
      next: () => {
        this.favoriteStore.invalidate();
        this.loadFavorites();
      },
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

  // AP-34: 自分のアカウントを退会（匿名化）する。成功後はログアウトしてSC-01（ログイン）へ遷移する
  protected withdraw(): void {
    if (!confirm('退会しますか？この操作は取り消せません。')) {
      return;
    }
    const userId = Number(this.dummyUserStore.currentUserId());
    this.withdrawing.set(true);
    this.userApi.anonymize(userId).subscribe({
      next: () => {
        this.dummyUserStore.logout();
        this.router.navigateByUrl('/login');
      },
      error: (err) => {
        this.withdrawing.set(false);
        alert(err.error?.message ?? '退会処理に失敗しました。');
      },
    });
  }
}
