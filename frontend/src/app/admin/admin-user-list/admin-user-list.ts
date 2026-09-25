// 実行環境: ブラウザ側。SC-13 利用者管理画面（/admin/users）。
// 登録済み利用者の一覧（AP-03）と、管理者アカウント登録フォーム（AP-25、D-04）を提供する。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { UserApiService, UserSummary } from '../../core/user-api';

interface FieldError {
  field: string;
  message: string;
}

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

  // SC-13: 管理者アカウント登録フォーム（AP-25）
  protected readonly registerName = signal('');
  protected readonly registerEmail = signal('');
  protected readonly registering = signal(false);
  protected readonly registerErrorMessage = signal<string | null>(null);
  protected readonly registerFieldErrors = signal<FieldError[]>([]);

  constructor(private readonly userApi: UserApiService) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  protected onRegisterNameInput(value: string): void {
    this.registerName.set(value);
  }

  protected onRegisterEmailInput(value: string): void {
    this.registerEmail.set(value);
  }

  protected registerFieldError(field: string): string | null {
    return this.registerFieldErrors().find((e) => e.field === field)?.message ?? null;
  }

  // AP-25: 既存の管理者のみ実行できる。作成されるのは常に管理者
  protected registerAdmin(): void {
    this.registerErrorMessage.set(null);
    this.registerFieldErrors.set([]);
    this.registering.set(true);

    this.userApi.registerAdmin(this.registerName(), this.registerEmail()).subscribe({
      next: () => {
        this.registering.set(false);
        this.registerName.set('');
        this.registerEmail.set('');
        this.loadUsers();
      },
      error: (err) => {
        this.registering.set(false);
        if (err.status === 400 && err.error?.errors) {
          this.registerFieldErrors.set(err.error.errors);
        } else {
          this.registerErrorMessage.set(err.error?.message ?? '登録に失敗しました。');
        }
      },
    });
  }

  private loadUsers(): void {
    this.loading.set(true);
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
