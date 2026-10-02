package com.example.eventapp.common.exception;

/**
 * 404 Not Found: 指定されたIDのデータが存在しない場合に投げる例外。
 * GlobalExceptionHandlerの{@code handleNotFound}がこれをキャッチし、HTTP 404に変換する。
 */
// 実行環境: サーバー側（JVM）。404: 指定IDのリソースが存在しない（docs/03_API設計書.md 2.4「不在=404」）。
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        // BusinessExceptionと同様、親クラスにメッセージを渡すだけ
        super(message);
    }
}
