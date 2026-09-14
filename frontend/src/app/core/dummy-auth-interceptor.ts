// 実行環境: ブラウザ側。すべてのAPIリクエストにダミー認証ヘッダ（X-User-Id）を自動で付ける関数型インターセプター。
// API設計書§0の認証方式（X-User-Idヘッダ）に対応。
// どのuserIdを付けるかはDummyUserStoreが持つ（ナビの切り替えUIで変更可能）。
// SC-01（ログイン画面）実装後、実際にログインしたuserIdに置き換える。
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { DummyUserStore } from './dummy-user-store';

export const dummyAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const userId = inject(DummyUserStore).currentUserId();
  const withAuthHeader = req.clone({
    setHeaders: { 'X-User-Id': userId },
  });
  return next(withAuthHeader);
};
