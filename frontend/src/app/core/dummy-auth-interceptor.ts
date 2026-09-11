// 実行環境: ブラウザ側。すべてのAPIリクエストにダミー認証ヘッダ（X-User-Id）を自動で付ける関数型インターセプター。
// API設計書§0の認証方式（X-User-Idヘッダ）に対応。
// 暫定でuserId=1（一般ユーザー）固定。SC-01（ログイン画面）実装後、実際にログインしたuserIdに置き換える。
import { HttpInterceptorFn } from '@angular/common/http';

export const dummyAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const withAuthHeader = req.clone({
    setHeaders: { 'X-User-Id': '1' },
  });
  return next(withAuthHeader);
};
