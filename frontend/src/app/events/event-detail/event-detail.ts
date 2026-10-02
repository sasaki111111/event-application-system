// 実行環境: ブラウザ側。SC-02の詳細部分（イベント詳細＋申込）。
// `ActivatedRoute`は、現在表示中のURL（/events/:id等）のパラメータ（:idの値）や
// クエリパラメータ（?from=search等）を読み取るためのAngularのService。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EventApiService, EventDetail as EventDetailModel } from '../../core/event-api';
import { ApplicationApiService } from '../../core/application-api';
import { FavoriteStore } from '../../core/favorite-store';
import { CommentApiService, CommentNode, buildCommentTree } from '../../core/comment-api';
import { DummyUserStore } from '../../core/dummy-user-store';
import { CommentItem, CommentReplyEvent } from '../comment-item/comment-item';

/**
 * SC-02イベント詳細画面（イベント詳細の表示・申込・お気に入り・コメント）を担当するComponent。
 * `implements OnInit`は、`ngOnInit`というライフサイクルフックのメソッドを必ず持つことを
 * TypeScriptに伝える宣言（下のngOnInitで説明）。
 *
 * 使用するAngular Service:
 * - `ActivatedRoute`: URLの`:id`パラメータ（表示するイベントID）と、戻り先を決めるための
 *   クエリパラメータ（`from=search`等）を読み取る。
 * - `Router`: 申込完了後に申込完了画面へ遷移するために使う。
 * - `EventApiService`: イベント詳細取得API（API-02）の呼び出し。
 * - `ApplicationApiService`: 申込API（API-03）の呼び出し。
 * - `FavoriteStore`: お気に入り登録状態を画面間で共有する状態管理（登録・解除もここから行う）。
 * - `CommentApiService`: コメントの一覧取得・投稿・削除APIの呼び出し。
 * - `DummyUserStore`: ログイン中ユーザーが管理者かどうかの判定に使う。
 *
 * 画面遷移: 起点はevent-list.ts（一覧のリンク）やevent-search.ts（検索結果のリンク）。
 * 申込成功時はapply-done.ts（申込完了画面）へ遷移する。「戻る」リンクの行き先は
 * 遷移元（一覧／検索／カレンダー）によって変わる（backLink/backQueryParams参照）。
 */
@Component({
  selector: 'app-event-detail',
  imports: [CommonModule, RouterLink, CommentItem],
  templateUrl: './event-detail.html',
  styleUrl: './event-detail.css',
})
export class EventDetail implements OnInit {
  protected readonly event = signal<EventDetailModel | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly applying = signal(false);
  protected readonly applyErrorMessage = signal<string | null>(null);

  // 機能追加（定員区分・アンケート）: 申込フォームの入力状態
  protected readonly selectedTicketTypeId = signal<number | null>(null);
  protected readonly extraAnswer = signal('');

  // 機能追加（お気に入り）: 登録済みかどうかはFavoriteStore（画面間で共有）から参照する
  protected readonly favoriteBusy = signal(false);

  // 機能追加（イベントコメント）。commentTreeは返信を親子構造に組み立てたもの（表示用）
  protected readonly commentTree = signal<CommentNode[]>([]);
  protected readonly commentBody = signal('');
  protected readonly postingComment = signal(false);
  protected readonly commentErrorMessage = signal<string | null>(null);

  private eventId = 0;

  // 検索画面（/events/search）から来た場合は「戻る」の行き先をそちらにする（?from=searchで判定）。
  // カレンダー表示（/events?view=calendar）から来た場合は、同じ表示モード・月に戻す（?from=calendarで判定）
  protected readonly backLink = signal<string>('/events');
  protected readonly backLabel = signal<string>('← イベント一覧に戻る');
  protected readonly backQueryParams = signal<Record<string, string>>({});

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly eventApi: EventApiService,
    private readonly applicationApi: ApplicationApiService,
    protected readonly favoriteStore: FavoriteStore,
    private readonly commentApi: CommentApiService,
    protected readonly dummyUserStore: DummyUserStore,
  ) {}

  // 要件定義書E7: 管理者は申込できない。ボタン自体を出さない。role基準で判定する
  protected get isAdmin(): boolean {
    return this.dummyUserStore.isAdmin();
  }

  /**
   * ngOnInitは、Angularのライフサイクルフックの一つ。Componentが画面に表示される
   * 直前に一度だけ自動的に実行される（コンストラクタとは別物で、他のinputやDIの
   * 準備が整った後に呼ばれる初期化処理を書く場所）。
   * ここでは、URLからイベントIDと戻り先情報を取り出し、イベント詳細・お気に入り状態・
   * コメント一覧の取得を開始している。
   */
  ngOnInit(): void {
    this.eventId = Number(this.route.snapshot.paramMap.get('id'));

    const from = this.route.snapshot.queryParamMap.get('from');
    if (from === 'search') {
      this.backLink.set('/events/search');
      this.backLabel.set('← イベント検索に戻る');
    } else if (from === 'calendar') {
      const month = this.route.snapshot.queryParamMap.get('month');
      this.backQueryParams.set(month ? { view: 'calendar', month } : { view: 'calendar' });
    }

    // `.subscribe({ next, error })`は、Observable（非同期で届くデータの流れ）の結果を
    // 受け取るための書き方。HTTPリクエストの結果はすぐに戻らないため、成功時はnext、
    // 失敗時はerrorに渡した処理が後から実行される。
    this.eventApi.detail(this.eventId).subscribe({
      next: (event) => {
        this.event.set(event);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? 'イベント詳細の取得に失敗しました。');
        this.loading.set(false);
      },
    });

    // 機能追加（お気に入り）: 一般ユーザー・管理者の両方が使える（要件定義書§4）。取得失敗はボタン未反映のままになるだけ
    this.favoriteStore.ensureLoaded().subscribe({ error: () => {} });

    this.loadComments();
  }

  // 機能追加（イベントコメント）: 開催前後を問わず投稿可能（要件定義書§4）。
  // APIはフラットな配列で返すため、表示用に親子構造（commentTree）へ組み立てる
  /** コメント一覧を取得し直す。コメントの投稿・返信・削除が成功した後に毎回呼ばれる。 */
  private loadComments(): void {
    this.commentApi.list(this.eventId).subscribe({
      next: (comments) => this.commentTree.set(buildCommentTree(comments)),
      error: () => {
        // コメント取得失敗はイベント詳細本体の表示をブロックしない
      },
    });
  }

  /** コメント投稿欄の`(input)`イベントで呼ばれる。 */
  protected onCommentBodyInput(value: string): void {
    this.commentBody.set(value);
  }

  /** 「投稿」ボタン（(click)）で呼ばれる。入力済みのコメント本文を投稿し、成功したら一覧を再取得する。 */
  protected postComment(): void {
    const body = this.commentBody().trim();
    if (!body) {
      return;
    }

    this.commentErrorMessage.set(null);
    this.postingComment.set(true);

    this.commentApi.post(this.eventId, body).subscribe({
      next: () => {
        this.commentBody.set('');
        this.postingComment.set(false);
        this.loadComments();
      },
      error: (err) => {
        this.postingComment.set(false);
        this.commentErrorMessage.set(err.error?.message ?? 'コメントの投稿に失敗しました。');
      },
    });
  }

  // comment-itemコンポーネントからバブルしてきた返信イベント（何階層目の返信でも同じハンドラで受ける）
  /** app-comment-itemの`(reply)`出力イベントで呼ばれる。返信を投稿し、成功したらコメント一覧を再取得する。 */
  protected onReply(event: CommentReplyEvent): void {
    this.commentApi.post(this.eventId, event.body, event.parentCommentId).subscribe({
      next: () => this.loadComments(),
      error: (err) => alert(err.error?.message ?? '返信の投稿に失敗しました。'),
    });
  }

  // API-22: 投稿者本人または管理者のみ削除可能（要件定義書§8 E10）。ボタン自体はmineがtrueの時のみ表示。
  // 返信が残っている場合はサーバー側で論理削除される（一覧には残り、本文が削除済み表示に置き換わる）
  /** app-comment-itemの`(delete)`出力イベントで呼ばれる。確認ダイアログの後、コメントを削除する。 */
  protected onDeleteComment(commentId: number): void {
    if (!confirm('このコメントを削除しますか？')) {
      return;
    }
    this.commentApi.remove(commentId).subscribe({
      next: () => this.loadComments(),
      error: (err) => alert(err.error?.message ?? 'コメントの削除に失敗しました。'),
    });
  }

  // 機能追加（お気に入り）: 登録・解除はどちらも冪等（要件定義書§8 E9）
  /** 「☆/★ お気に入り」ボタン（(click)）で呼ばれる。登録済みなら解除、未登録なら登録する。 */
  protected toggleFavorite(): void {
    this.favoriteBusy.set(true);
    this.favoriteStore.toggle(this.eventId).subscribe({
      next: () => this.favoriteBusy.set(false),
      error: (err) => {
        this.favoriteBusy.set(false);
        alert(err.error?.message ?? 'お気に入りの更新に失敗しました。');
      },
    });
  }

  /** 参加区分の`<select>`の`(change)`イベントで呼ばれる。選択された区分IDをsignalに反映する。 */
  protected onTicketTypeChange(value: string): void {
    this.selectedTicketTypeId.set(value ? Number(value) : null);
  }

  /** 申込時アンケートの`<textarea>`の`(input)`イベントで呼ばれる。 */
  protected onExtraAnswerInput(value: string): void {
    this.extraAnswer.set(value);
  }

  // API-03: 定員超過・締切超過・二重申込は業務エラー（400、message付き）としてバックエンドが返す。
  // 区分があるイベントで未選択のまま送信すると400「区分を選択してください」（要件定義書E12）
  /** 「申し込む」ボタン（(click)）で呼ばれる。申込APIを呼び、成功したら申込完了画面へ遷移する。 */
  protected apply(): void {
    this.applyErrorMessage.set(null);
    this.applying.set(true);

    const ticketTypeId = this.selectedTicketTypeId() ?? undefined;
    const extraAnswer = this.extraAnswer().trim() || undefined;

    this.applicationApi.apply(this.eventId, ticketTypeId, extraAnswer).subscribe({
      next: (application) => {
        this.router.navigate(['/events', this.eventId, 'done'], { state: { application } });
      },
      error: (err) => {
        this.applying.set(false);
        this.applyErrorMessage.set(err.error?.message ?? '申込に失敗しました。');
      },
    });
  }
}
