// 実行環境: ブラウザ側。すべてのAPIリクエストにダミー認証ヘッダ（X-User-Id）を自動で付ける関数型インターセプター。
// API設計書§0の認証方式（X-User-Idヘッダ）に対応。
// どのuserIdを付けるかはDummyUserStoreが持つ（SC-01ログイン画面で選んだロール、E-2）。
// 未ログイン時はヘッダを付けない（authGuardで保護ルートには来ない想定だが、念のためbackend側の401に委ねる）。
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { DummyUserStore } from './dummy-user-store';

export const dummyAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const userId = inject(DummyUserStore).currentUserId();
  if (userId === null) {
    return next(req);
  }
  const withAuthHeader = req.clone({
    setHeaders: { 'X-User-Id': userId },
  });
  return next(withAuthHeader);
};
