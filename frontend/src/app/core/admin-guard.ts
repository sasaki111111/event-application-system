// 実行環境: ブラウザ側。/admin/**への画面遷移を管理者以外は通さないルートガード（E-7）。
// 画面遷移図「管理者以外が/admin/*にアクセス→イベント一覧」に対応。ルート側でauthGuardの後に適用するため、
// ここに来る時点でログイン済み（currentUserIdはnullでない）であることが前提。
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { DummyUserStore } from './dummy-user-store';

export const adminGuard: CanActivateFn = () => {
  const dummyUserStore = inject(DummyUserStore);
  if (dummyUserStore.isAdmin()) {
    return true;
  }
  return inject(Router).createUrlTree(['/events']);
};
