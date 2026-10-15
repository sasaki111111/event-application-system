// 実行環境: ブラウザ側。SC-022のイベント管理一覧（E-5）。
// GET /api/eventsはSC-020と同じAPIを流用する（API設計書の備考の通り）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { EventApiService, EventSummary } from '../../core/event-api';

@Component({
  selector: 'app-admin-event-list',
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-event-list.html',
  styleUrl: './admin-event-list.css',
})
/**
 * SC-022 イベント管理一覧画面（/admin/events）を担当するComponent。
 * 管理者が主催する全イベント（受付中・受付終了を含む）をカード形式で一覧表示する。
 *
 * - ルーティング定義（app.routes.ts）でこのパスには authGuard・adminGuard が設定されており、
 *   管理者以外のユーザーはアクセスできない（＝管理者専用画面）。
 * - 利用するAngular Service: EventApiService（一覧取得・削除・複製元の詳細取得）。
 * - 画面遷移（いずれもRouterLinkまたはrouter.navigateで実装）:
 *   - 「＋ 新規登録」→ /admin/events/new（AdminEventForm）
 *   - 「削除済みイベント」→ /admin/events/deleted（AdminDeletedEvents）
 *   - 各カードのイベント名 → /admin/events/:id/edit（AdminEventForm、編集モード）
 *   - 「受付」→ /admin/events/:id/checkin（AdminCheckin）
 *   - 「複製」→ イベント詳細取得後、/admin/events/new へ複製元データを持って遷移
 */
export class AdminEventList implements OnInit {
  protected readonly events = signal<EventSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  // 複製処理中の対象イベントID。nullなら複製処理中の行は無い（ボタンの「複製準備中...」表示に使う）
  protected readonly duplicatingId = signal<number | null>(null);

  constructor(
    private readonly eventApi: EventApiService,
    private readonly router: Router,
  ) {}

  /**
   * 各カードの「複製」ボタン（(click)="duplicate(event)"）から呼ばれる処理。
   * 複製元イベントの詳細（AP-021）を取得し、新規登録フォームへ値を持って遷移する。
   * バックエンドAPIは呼ばない（開催日時・申込締切は複製対象外のため、新規登録と同じ入力チェックを通す）
   */
  protected duplicate(event: EventSummary): void {
    // どのイベントを複製処理中かをsignalに記録する（ボタンの表示切り替えに使う）
    this.duplicatingId.set(event.id);
    // 一覧には全項目が含まれていないため、複製元として使うイベント詳細を取得する
    this.eventApi.detail(event.id).subscribe({
      next: (detail) => {
        // 処理中状態を解除し、取得した詳細をstateとして渡しつつ新規登録フォームへ遷移する
        this.duplicatingId.set(null);
        this.router.navigate(['/admin/events/new'], { state: { duplicateFrom: detail } });
      },
      error: (err) => {
        // 失敗したら処理中状態を解除し、エラーをアラートで表示する
        this.duplicatingId.set(null);
        alert(err.error?.message ?? '複製に失敗しました。');
      },
    });
  }

  /**
   * Angularのライフサイクルフックの一つ。Componentが画面に表示される直前に一度だけ実行される。
   * ここではイベント一覧の初回読み込みを行う。
   */
  ngOnInit(): void {
    this.loadEvents();
  }

  /**
   * 各カードの「削除」ボタン（(click)="deleteEvent(event)"）から呼ばれる処理。
   * confirm()で確認ダイアログを出し、OKされた場合のみ削除APIを呼ぶ（論理削除、後で復元可能）。
   */
  protected deleteEvent(event: EventSummary): void {
    // 確認ダイアログでキャンセルされたらここで処理を中断する
    if (!confirm(`「${event.name}」を削除しますか？（削除済みイベント画面から後で復元できます）`)) {
      return;
    }
    // 削除（論理削除）APIを呼び出す
    this.eventApi.remove(event.id).subscribe({
      // 成功したら一覧を再取得する（削除したイベントは一覧から消える）
      next: () => this.loadEvents(),
      error: (err) => {
        // 受付済の申込がある場合は400（業務エラー）、権限が無ければ403が返る（API設計書 API-08）
        const message = err.error?.message ?? '削除に失敗しました。';
        alert(message);
      },
    });
  }

  /** イベント一覧（全件、受付中・受付終了を含む）をAPIから取得し直す。 */
  private loadEvents(): void {
    // 読み込み中表示に切り替える
    this.loading.set(true);
    // 全件（'all'）のイベント一覧を取得する
    this.eventApi.list('all').subscribe({
      next: (events) => {
        // 取得した一覧をsignalに反映し、読み込み中表示を終える
        this.events.set(events);
        this.loading.set(false);
      },
      error: (err) => {
        // 取得失敗時はエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? 'イベント一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
