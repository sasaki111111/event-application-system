// 実行環境: ブラウザ側。管理者ダッシュボード画面（/admin/dashboard、機能追加）。
// 既存のAPI（イベント一覧・ユーザー一覧・申込実績集計）に加え、AP-29（お気に入り総数）・
// AP-30（コメント総数）を組み合わせて表示する。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { EventApiService } from '../../core/event-api';
import { UserApiService } from '../../core/user-api';
import { ReportApiService } from '../../core/report-api';
import { FavoriteApiService } from '../../core/favorite-api';
import { CommentApiService } from '../../core/comment-api';

@Component({
  selector: 'app-admin-dashboard',
  imports: [CommonModule],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.css',
})
/**
 * 管理者ダッシュボード画面（/admin/dashboard）を担当するComponent。
 * ログイン後、管理者が最初に見る集計サマリー画面。
 *
 * - ルーティング定義（app.routes.ts）でこのパスには authGuard・adminGuard が設定されており、
 *   管理者以外のユーザーはアクセスできない（＝管理者専用画面）。
 * - 利用するAngular Service: EventApiService（イベント数）、UserApiService（ユーザー数）、
 *   ReportApiService（受付済申込数の集計）、FavoriteApiService（お気に入り総数）、
 *   CommentApiService（コメント総数）。5つのServiceから集計値だけを取り出して表示する。
 * - 画面遷移: この画面自体には他画面への遷移リンクは無い。ログイン画面からロール判定で
 *   管理者はこの画面に遷移してくる（login.ts参照）。
 */
export class AdminDashboard implements OnInit {
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly totalEvents = signal(0);
  protected readonly totalUsers = signal(0);
  protected readonly totalAcceptedApplications = signal(0);
  protected readonly totalFavorites = signal(0);
  protected readonly totalComments = signal(0);

  constructor(
    private readonly eventApi: EventApiService,
    private readonly userApi: UserApiService,
    private readonly reportApi: ReportApiService,
    private readonly favoriteApi: FavoriteApiService,
    private readonly commentApi: CommentApiService,
  ) {}

  /**
   * Angularのライフサイクルフックの一つ。Componentが画面に表示される直前に一度だけ実行される。
   * ここでは5つのAPI呼び出しを行い、結果を集計値のsignalに反映している。
   *
   * forkJoin()はRxJS（Observableを扱うライブラリ）の関数で、複数のObservable（非同期処理）を
   * 並行して実行し、すべてが完了したタイミングでまとめて結果を受け取れるようにする。
   * ここでは events/users/reports/favorites/comments の5つのAPI呼び出しを同時に投げて、
   * 全部終わった時点で一度だけ.subscribe()のnextが呼ばれる。
   */
  ngOnInit(): void {
    // 5つのAPI呼び出し（イベント一覧、利用者一覧、申込実績集計、お気に入り数、コメント数）を
    // forkJoinでまとめて並行実行し、全部完了したらnextが1回だけ呼ばれる
    forkJoin({
      events: this.eventApi.list('all'),
      users: this.userApi.list(),
      reports: this.reportApi.summary('startAt'),
      favorites: this.favoriteApi.count(),
      comments: this.commentApi.count(),
    }).subscribe({
      next: ({ events, users, reports, favorites, comments }) => {
        // イベント件数・利用者件数はそれぞれの配列の長さをそのまま使う
        this.totalEvents.set(events.length);
        this.totalUsers.set(users.length);
        // 受付済申込数は、イベントごとの集計（reports）のacceptedCountを全イベント分合計する
        this.totalAcceptedApplications.set(reports.reduce((sum, r) => sum + r.acceptedCount, 0));
        // お気に入り総数・コメント総数はAPIが返す集計値（count）をそのまま使う
        this.totalFavorites.set(favorites.count);
        this.totalComments.set(comments.count);
        // 全て反映できたので読み込み中表示を終える
        this.loading.set(false);
      },
      error: (err) => {
        // いずれか1つでも失敗したらエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? 'ダッシュボードの取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
