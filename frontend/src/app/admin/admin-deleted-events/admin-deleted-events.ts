// 実行環境: ブラウザ側。機能追加（ソフトデリート）: 削除済みイベントの確認・復元画面（/admin/events/deleted）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DeletedEvent, EventApiService } from '../../core/event-api';

@Component({
  selector: 'app-admin-deleted-events',
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-deleted-events.html',
  styleUrl: './admin-deleted-events.css',
})
/**
 * 削除済みイベントの確認・復元画面（/admin/events/deleted）を担当するComponent。
 *
 * - ルーティング定義（app.routes.ts）でこのパスには authGuard・adminGuard が設定されており、
 *   管理者以外のユーザーはアクセスできない（＝管理者専用画面）。
 * - 利用するAngular Service: EventApiService（削除済みイベント一覧の取得、復元）。
 * - 画面遷移:
 *   - 「← イベント管理に戻る」（RouterLink）で /admin/events へ戻る。
 *   - 「復元」ボタンでAPIを呼び、一覧をその場で再読み込みする（画面遷移はしない）。
 *   - 「複製」ボタンはrouter.navigate()で /admin/events/new （新規イベント登録画面）へ遷移し、
 *     選んだ削除済みイベントの内容を複製元として渡す（state経由）。
 */
export class AdminDeletedEvents implements OnInit {
  protected readonly events = signal<DeletedEvent[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly eventApi: EventApiService,
    private readonly router: Router,
  ) {}

  /**
   * Angularのライフサイクルフックの一つ。Componentが画面に表示される直前に一度だけ実行される。
   * ここでは削除済みイベント一覧の初回読み込みを行う。
   */
  ngOnInit(): void {
    this.loadDeletedEvents();
  }

  /**
   * 各カードの「複製」ボタン（(click)="duplicate(event)"）から呼ばれる処理。
   * router.navigate()はAngularのRouterを使ったコード上での画面遷移で、第2引数のstateに
   * 渡した値は遷移先のComponent（AdminEventForm）がActivatedRouteやhistory.stateから読み取れる。
   * 削除済み一覧（AP-06、複製に必要な項目を含む）はAPIを追加で呼ばずそのまま複製元にできる
   */
  protected duplicate(event: DeletedEvent): void {
    // /admin/events/new へ遷移しつつ、選んだ削除済みイベントをstateとして渡す（APIは呼ばない）
    this.router.navigate(['/admin/events/new'], { state: { duplicateFrom: event } });
  }

  /**
   * 各カードの「復元」ボタン（(click)="restoreEvent(event)"）から呼ばれる処理。
   * confirm()で確認ダイアログを出し、OKされた場合のみ復元APIを呼び、成功したら一覧を再読み込みする。
   */
  protected restoreEvent(event: DeletedEvent): void {
    // 確認ダイアログでキャンセルされたらここで処理を中断する
    if (!confirm(`「${event.name}」を復元しますか？`)) {
      return;
    }
    // 復元APIを呼び出す
    this.eventApi.restore(event.id).subscribe({
      // 成功したら一覧を再取得する（復元したイベントは一覧から消える）
      next: () => this.loadDeletedEvents(),
      error: (err) => {
        // 失敗したらエラー内容をアラートで表示する
        const message = err.error?.message ?? '復元に失敗しました。';
        alert(message);
      },
    });
  }

  /** 削除済みイベント一覧をAPIから取得し直す（初回表示時・復元後の再表示時に呼ばれる）。 */
  private loadDeletedEvents(): void {
    // 読み込み中表示に切り替える
    this.loading.set(true);
    // 削除済みイベント一覧を取得する
    this.eventApi.listDeleted().subscribe({
      next: (events) => {
        // 取得した一覧をsignalに反映し、読み込み中表示を終える
        this.events.set(events);
        this.loading.set(false);
      },
      error: (err) => {
        // 取得失敗時はエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? '削除済みイベント一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
