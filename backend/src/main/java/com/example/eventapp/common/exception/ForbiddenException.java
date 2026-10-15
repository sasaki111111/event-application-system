package com.example.eventapp.common.exception;

/**
 * 403 Forbidden: ログインはしているが、ロール（権限）が不足している場合に投げる例外。
 * GlobalExceptionHandlerの{@code handleForbidden}がこれをキャッチし、HTTP 403に変換する。
 */
// 実行環境: サーバー側（JVM）。403: 権限不足（docs/30_詳細設計/33_共通詳細設計書.md 3.3のE-A-002〜E-A-005、E-A-007）。
// 管理者専用APIへの一般利用者のアクセスに加え、本人以外の申込のキャンセル・コメント削除・退会や、管理者による申込でも使う。
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        // BusinessExceptionと同様、親クラスにメッセージを渡すだけ
        super(message);
    }
}
