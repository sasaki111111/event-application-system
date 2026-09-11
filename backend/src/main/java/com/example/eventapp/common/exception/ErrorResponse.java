package com.example.eventapp.common.exception;

import java.time.OffsetDateTime;
import java.util.List;

// 実行環境: サーバー側（JVM）。API設計書§0の共通エラーレスポンス形式。
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        List<FieldError> errors
) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, null);
    }

    public static ErrorResponse ofValidation(int status, String error, String message, List<FieldError> errors) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, errors);
    }
}
