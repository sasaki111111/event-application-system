// 実行環境: ブラウザ側。SC-02の詳細部分（イベント詳細、読み取り専用の先行実装）。
// 「申し込む」ボタンはD-3（申込API）実装後に追加する。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EventApiService, EventDetail as EventDetailModel } from '../../core/event-api';

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

  constructor(
    private readonly route: ActivatedRoute,
    private readonly eventApi: EventApiService,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.eventApi.detail(id).subscribe({
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
}
