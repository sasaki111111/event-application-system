package com.example.eventapp.common.exception;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * エラー発生時にクライアント（Angularフロントエンド）へ返すレスポンス内容を表すDTO
 * （Data Transfer Object＝データを受け渡すためだけの入れ物クラス）。
 * GlobalExceptionHandlerがこのクラスのインスタンスを組み立て、JSONに変換してHTTPレスポンスとして返す。
 */
// 実行環境: サーバー側（JVM）。docs/03_API設計書.md 2.3節の共通エラーレスポンス形式。
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        List<FieldError> errors
) {

    // 入力エラー時の「どの項目が」「どう不正か」を表す入れ子のrecord
    public record FieldError(String field, String message) {
    }

    // フィールドエラー無しの単純なエラー（認証・権限・業務例外等）を組み立てる。
    // timestampは呼ばれた瞬間の現在時刻、errorsはnull（該当データ無し）とする
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, null);
    }

    // フィールドエラー付き（入力チェックエラー等）のエラーを組み立てる
    public static ErrorResponse ofValidation(int status, String error, String message, List<FieldError> errors) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, errors);
    }
}
