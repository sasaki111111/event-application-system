// 実行環境: ブラウザ側。ユーザー一覧画面（/admin/users、機能追加：マスタ確認用）。
// 動作確認時に「今どんなユーザーが登録されているか」をDBに直接問い合わせずに見られるようにするための画面。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { UserApiService, UserSummary } from '../../core/user-api';

@Component({
  selector: 'app-admin-user-list',
  imports: [CommonModule],
  templateUrl: './admin-user-list.html',
  styleUrl: './admin-user-list.css',
})
export class AdminUserList implements OnInit {
  protected readonly users = signal<UserSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  constructor(private readonly userApi: UserApiService) {}

  ngOnInit(): void {
    this.userApi.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? 'ユーザー一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
