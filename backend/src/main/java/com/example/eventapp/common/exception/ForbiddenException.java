package com.example.eventapp.common.exception;

/**
 * 403 Forbidden: ログインはしているが、ロール（権限）が不足している場合に投げる例外。
 * GlobalExceptionHandlerの{@code handleForbidden}がこれをキャッチし、HTTP 403に変換する。
 */
// 実行環境: サーバー側（JVM）。403: ロールに基づく権限不足（docs/03_API設計書.md 2.2「権限がありません」）。
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        // BusinessExceptionと同様、親クラスにメッセージを渡すだけ
        super(message);
    }
}
