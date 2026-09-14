// 実行環境: ブラウザ側。未ログインで保護ルートに来た場合に/loginへ戻すルートガード（E-2）。
// 画面遷移図「全保護ルートにauthGuard」に対応。
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { DummyUserStore } from './dummy-user-store';

export const authGuard: CanActivateFn = () => {
  const dummyUserStore = inject(DummyUserStore);
  if (dummyUserStore.currentUserId() !== null) {
    return true;
  }
  return inject(Router).createUrlTree(['/login']);
};
