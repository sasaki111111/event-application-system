// 実行環境: ブラウザ側。SC-02の詳細部分（イベント詳細＋申込）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EventApiService, EventDetail as EventDetailModel } from '../../core/event-api';
import { ApplicationApiService } from '../../core/application-api';
import { FavoriteApiService } from '../../core/favorite-api';
import { CommentApiService, EventComment } from '../../core/comment-api';
import { DummyUserStore } from '../../core/dummy-user-store';

@Component({
  selector: 'app-event-detail',
  imports: [CommonModule, RouterLink],
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

  // 機能追加（お気に入り）
  protected readonly isFavorited = signal(false);
  protected readonly favoriteBusy = signal(false);

  // 機能追加（イベントコメント）
  protected readonly comments = signal<EventComment[]>([]);
  protected readonly commentBody = signal('');
  protected readonly postingComment = signal(false);
  protected readonly commentErrorMessage = signal<string | null>(null);

  private eventId = 0;

  // 検索画面（/events/search）から来た場合は「戻る」の行き先をそちらにする（?from=searchで判定）
  protected readonly backLink = signal<string>('/events');
  protected readonly backLabel = signal<string>('← イベント一覧に戻る');

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly eventApi: EventApiService,
    private readonly applicationApi: ApplicationApiService,
    private readonly favoriteApi: FavoriteApiService,
    private readonly commentApi: CommentApiService,
    protected readonly dummyUserStore: DummyUserStore,
  ) {}

  // 要件定義書E7: 管理者は申込できない。ボタン自体を出さない
  protected get isAdmin(): boolean {
    return this.dummyUserStore.currentUserId() === '2';
  }

  ngOnInit(): void {
    this.eventId = Number(this.route.snapshot.paramMap.get('id'));

    if (this.route.snapshot.queryParamMap.get('from') === 'search') {
      this.backLink.set('/events/search');
      this.backLabel.set('← イベント検索に戻る');
    }

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

    // 機能追加（お気に入り）: 一般ユーザー・管理者の両方が使える（要件定義書§4）
    this.favoriteApi.myFavorites().subscribe({
      next: (favorites) => this.isFavorited.set(favorites.some((f) => f.id === this.eventId)),
      error: () => {
        // 取得失敗時はボタンが未反映（お気に入り登録済みでも☆表示）のままになるだけ
      },
    });

    this.loadComments();
  }

  // 機能追加（イベントコメント）: 開催前後を問わず投稿可能（要件定義書§4）
  private loadComments(): void {
    this.commentApi.list(this.eventId).subscribe({
      next: (comments) => this.comments.set(comments),
      error: () => {
        // コメント取得失敗はイベント詳細本体の表示をブロックしない
      },
    });
  }

  protected onCommentBodyInput(value: string): void {
    this.commentBody.set(value);
  }

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

  // API-22: 投稿者本人または管理者のみ削除可能（要件定義書§8 E10）。ボタン自体はmineがtrueの時のみ表示
  protected deleteComment(comment: EventComment): void {
    if (!confirm('このコメントを削除しますか？')) {
      return;
    }
    this.commentApi.remove(comment.id).subscribe({
      next: () => this.loadComments(),
      error: (err) => alert(err.error?.message ?? 'コメントの削除に失敗しました。'),
    });
  }

  // 機能追加（お気に入り）: 登録・解除はどちらも冪等（要件定義書§8 E9）
  protected toggleFavorite(): void {
    this.favoriteBusy.set(true);
    const onSuccess = (favorited: boolean) => {
      this.isFavorited.set(favorited);
      this.favoriteBusy.set(false);
    };
    const onError = (err: { error?: { message?: string } }) => {
      this.favoriteBusy.set(false);
      alert(err.error?.message ?? 'お気に入りの更新に失敗しました。');
    };

    if (this.isFavorited()) {
      this.favoriteApi.remove(this.eventId).subscribe({ next: () => onSuccess(false), error: onError });
    } else {
      this.favoriteApi.add(this.eventId).subscribe({ next: () => onSuccess(true), error: onError });
    }
  }

  protected onTicketTypeChange(value: string): void {
    this.selectedTicketTypeId.set(value ? Number(value) : null);
  }

  protected onExtraAnswerInput(value: string): void {
    this.extraAnswer.set(value);
  }

  // API-03: 定員超過・締切超過・二重申込は業務エラー（400、message付き）としてバックエンドが返す。
  // 区分があるイベントで未選択のまま送信すると400「区分を選択してください」（要件定義書E12）
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
