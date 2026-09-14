// 実行環境: ブラウザ側。SC-03のマイページ（D-4対応、一覧表示のみの先行実装）。
// キャンセル操作はD-5（申込キャンセルAPI）実装後に追加する。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApplicationApiService, MyApplication } from '../core/application-api';

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

  constructor(private readonly applicationApi: ApplicationApiService) {}

  ngOnInit(): void {
    this.applicationApi.myApplications().subscribe({
      next: (applications) => {
        this.applications.set(applications);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('申込一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
