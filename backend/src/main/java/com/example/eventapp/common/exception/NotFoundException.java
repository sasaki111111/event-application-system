package com.example.eventapp.common.exception;

/**
 * 404 Not Found: 指定されたIDのデータが存在しない場合に投げる例外。
 * GlobalExceptionHandlerの{@code handleNotFound}がこれをキャッチし、HTTP 404に変換する。
 */
// 実行環境: サーバー側（JVM）。404: 指定IDのデータが存在しない、または削除済み（docs/30_詳細設計/33_共通詳細設計書.md 3.2のデータ未検出エラー）。
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        // BusinessExceptionと同様、親クラスにメッセージを渡すだけ
        super(message);
    }
}
