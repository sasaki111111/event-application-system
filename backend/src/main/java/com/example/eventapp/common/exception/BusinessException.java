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
 * 400 Bad Request: 業務ルール違反（定員超過・申込締切超過など、要件定義書§8）を表す例外。
 * {@code RuntimeException}を継承しているため、メソッドの宣言に{@code throws}を書かなくても
 * 自由にthrowできる（検査例外ではなく非検査例外）。
 */
// 実行環境: サーバー側（JVM）。400: 業務ルール違反（定員超過・締切超過等、要件定義書§8）。
// D以降でService層から送出する。
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        // 親クラスRuntimeExceptionのコンストラクタにメッセージを渡す。
        // このmessageが、GlobalExceptionHandlerで組み立てるErrorResponse.messageにそのまま使われる
        super(message);
    }
}
