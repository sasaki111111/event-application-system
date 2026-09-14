// 実行環境: ブラウザ側。/admin/**への画面遷移を管理者以外は通さないルートガード（E-7の先行実装）。
// 画面遷移図「管理者以外が/admin/*にアクセス→イベント一覧」に対応。
// 本物のログイン(E-2)ができたら、DummyUserStoreではなく実際のログイン結果を見るように置き換える。
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { DummyUserStore } from './dummy-user-store';

export const adminGuard: CanActivateFn = () => {
  const dummyUserStore = inject(DummyUserStore);
  if (dummyUserStore.currentUserId() === '2') {
    return true;
  }
  return inject(Router).createUrlTree(['/events']);
};
