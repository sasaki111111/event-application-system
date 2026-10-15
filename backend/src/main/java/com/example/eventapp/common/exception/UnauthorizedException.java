package com.example.eventapp.common.exception;

/**
 * 401 Unauthorized: そもそもログインできていない場合（X-User-Idヘッダが無い、値が不正、
 * 存在しない/退会済みのユーザーIDなど）に投げる例外。
 * GlobalExceptionHandlerの{@code handleUnauthorized}がこれをキャッチし、HTTP 401に変換する。
 */
// 実行環境: サーバー側（JVM）。401: X-User-Idヘッダ無し、または存在しないuserId（docs/30_詳細設計/31_API詳細設計書.md）。
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        // BusinessExceptionと同様、親クラスにメッセージを渡すだけ
        super(message);
    }
}
