package com.example.eventapp.common.exception;

// 実行環境: サーバー側（JVM）。403: ロールに基づく権限不足（docs/03_API設計書.md 2.2「権限がありません」）。
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
