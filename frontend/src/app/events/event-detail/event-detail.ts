// 実行環境: ブラウザ側。SC-02の詳細部分（イベント詳細＋申込）。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EventApiService, EventDetail as EventDetailModel } from '../../core/event-api';
import { ApplicationApiService } from '../../core/application-api';

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

  private eventId = 0;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly eventApi: EventApiService,
    private readonly applicationApi: ApplicationApiService,
  ) {}

  ngOnInit(): void {
    this.eventId = Number(this.route.snapshot.paramMap.get('id'));
    this.eventApi.detail(this.eventId).subscribe({
      next: (event) => {
        this.event.set(event);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(
          err.status === 404 ? 'イベントが見つかりません。' : 'イベント詳細の取得に失敗しました。',
        );
        this.loading.set(false);
      },
    });
  }

  // API-03: 定員超過・締切超過・二重申込は業務エラー（400、message付き）としてバックエンドが返す
  protected apply(): void {
    this.applyErrorMessage.set(null);
    this.applying.set(true);

    this.applicationApi.apply(this.eventId).subscribe({
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
