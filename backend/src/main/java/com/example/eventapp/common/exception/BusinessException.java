package com.example.eventapp.common.exception;

// このexceptionパッケージの5クラス（BusinessException/ForbiddenException/NotFoundException/
// UnauthorizedException/GlobalExceptionHandler）は、このプロジェクト独自の例外処理の仕組み。
// Javaの「例外（Exception）」とは、処理の実行中に発生した問題（想定外の入力・不正な状態など）を
// 呼び出し元に伝えるためのオブジェクトで、throwで投げて、呼び出し側のtry-catchやフレームワークが
// catchして対応する。ここではtry-catchを個々のControllerには書かず、業務的に意味のあるエラーごとに
// 専用の例外クラスを自作し（下記4クラス）、GlobalExceptionHandlerが一箇所でまとめて受け止めて
// 適切なHTTPレスポンスに変換する、という設計になっている（独自の例外クラスを作る理由は、
// 「権限が無い」「データが見つからない」等のエラーの種類をJavaの型として区別できるようにするため）。
/**
 * 400 Bad Request: 業務ルール違反（申込締切超過・二重申込など）を表す例外。
 * 業務ルールはdocs/10_要件定義/10_要件定義書.md 7章、判定方法はdocs/30_詳細設計/32_処理詳細設計書.md 5章、
 * エラーID（E-B-xxx）とメッセージはdocs/30_詳細設計/33_共通詳細設計書.md 3.2に定める。
 * なお定員超過は業務ルール違反にはせず、キャンセル待ちとして登録する（R-02）。
 * {@code RuntimeException}を継承しているため、メソッドの宣言に{@code throws}を書かなくても
 * 自由にthrowできる（検査例外ではなく非検査例外）。
 */
// 実行環境: サーバー側（JVM）。400: 業務ルール違反。Service層から送出する。
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        // 親クラスRuntimeExceptionのコンストラクタにメッセージを渡す。
        // このmessageが、GlobalExceptionHandlerで組み立てるErrorResponse.messageにそのまま使われる
        super(message);
    }
}
