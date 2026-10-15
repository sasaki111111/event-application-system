package com.example.eventapp.common.exception;

/**
 * 401 Unauthorized: 認証エラーを表す例外。次の2つの場面で投げる。
 * <ul>
 *   <li>AuthInterceptor: ログインできていない（X-User-Idヘッダが無い、値が不正、存在しない/退会済みのユーザーID。E-A-001）</li>
 *   <li>UserService#login: ログインに失敗した（メールアドレスが未登録、パスワードの不一致、退会済み。E-A-006）</li>
 * </ul>
 * GlobalExceptionHandlerの{@code handleUnauthorized}がこれをキャッチし、HTTP 401に変換する。
 */
// 実行環境: サーバー側（JVM）。401: 認証エラー（docs/30_詳細設計/33_共通詳細設計書.md 3.3・5.2）。
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        // BusinessExceptionと同様、親クラスにメッセージを渡すだけ
        super(message);
    }
}
