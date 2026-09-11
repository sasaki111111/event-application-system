package com.example.eventapp.common.exception;

// 403: ロールに基づく権限不足（API設計書§0「権限がありません」）
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
