package com.example.eventapp.common.exception;

import com.example.eventapp.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * {@code @RestControllerAdvice}が付いたこのクラスは、アプリ全体のControllerから投げられた例外を
 * 一箇所でキャッチし、HTTPレスポンス（ステータスコード＋エラーメッセージのJSON）に変換する
 * 「例外の集約地点」。{@code @ExceptionHandler(XxxException.class)}が付いたメソッドが、
 * そのクラス（またはサブクラス）の例外が投げられたときに自動的に呼ばれる。
 * このクラスで対応している例外とHTTPステータスの対応は次の通り。
 * <ul>
 *   <li>{@link UnauthorizedException} → 401 Unauthorized（未ログイン）</li>
 *   <li>{@link ForbiddenException} → 403 Forbidden（権限不足）</li>
 *   <li>{@link NotFoundException} → 404 Not Found（データが存在しない）</li>
 *   <li>{@link BusinessException} → 400 Bad Request（業務ルール違反）</li>
 *   <li>{@code MethodArgumentNotValidException}（{@code @Valid}による入力チェックエラー）
 *       および{@code ConstraintViolationException}（手動実行したBean Validationのエラー）
 *       → 400 Bad Request（入力エラー、フィールドごとのエラー内容付き）</li>
 *   <li>{@code HttpMessageNotReadableException}（不正なJSON等）、
 *       {@code MethodArgumentTypeMismatchException}（パスパラメータの型不一致）
 *       → 400 Bad Request</li>
 *   <li>上記以外の{@code Exception}全般 → 500 Internal Server Error（想定外のエラー）</li>
 * </ul>
 */
// 実行環境: サーバー側（JVM）。@RestControllerAdvice＝全Controllerで共通の例外ハンドラー。
// Service/Controllerが投げた例外を捕まえて、docs/30_詳細設計/31_API詳細設計書.mdで決めたJSON形式
// （timestamp/status/error/message[/errors]）に変換して返す。
// 認証・権限・業務ルール違反・入力エラー系の例外はいずれもここに集約されるため、
// docs/30_詳細設計/33_共通詳細設計書.mdのWARNログ（想定内だが注意が必要なとき）もここでまとめて出力する。
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuthContext authContext;

    public GlobalExceptionHandler(AuthContext authContext) {
        // ログ出力時にユーザーIDを埋め込むためAuthContextを保持する
        this.authContext = authContext;
    }

    // UnauthorizedExceptionが投げられたときに自動的に呼ばれるハンドラーメソッド
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        // 想定内だが注意が必要なエラーとしてWARNログを出す（誰が・どのAPIで失敗したか）
        warn(request, ex.getMessage());
        // 401 UnauthorizedのErrorResponseを組み立ててレスポンスとして返す
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // 以下、ForbiddenException/NotFoundException/BusinessExceptionも考え方は同じで、
    // 「WARNログを出す→対応するHTTPステータスでErrorResponseを組み立てて返す」という流れ
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        warn(request, ex.getMessage());
        // 403 Forbiddenとして返す
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        warn(request, ex.getMessage());
        // 404 Not Foundとして返す
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        warn(request, ex.getMessage());
        // 400 Bad Requestとして返す
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // @Valid付きの引数でBean Validationのエラーが1件でもあったときに呼ばれるハンドラー
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // 発生した全フィールドエラーを取り出し、toFieldError()で
        // ErrorResponse.FieldError（field名＋メッセージ）のリストに変換する
        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        warn(request, "入力エラー " + errors);
        // フィールドごとのエラー内容付きで400 Bad RequestのErrorResponseを組み立てる
        ErrorResponse body = ErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), "入力エラー", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // 権限チェックの後に手動実行するバリデーション（@Validを使うと権限チェックより先に走ってしまうため）
    // 考え方はhandleValidationと同様だが、ConstraintViolation（Bean Validationを手動実行した結果）から
    // 直接field名とメッセージを取り出す点が異なる
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        // 発生した全ConstraintViolationを、ErrorResponse.FieldError（field名＋メッセージ）のリストに変換する
        List<ErrorResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        warn(request, "入力エラー " + errors);
        ErrorResponse body = ErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), "入力エラー", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // docs/30_詳細設計/31_API詳細設計書.md「リクエスト形式エラー」: 不正なJSON・型不一致・必須ボディ欠落
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        warn(request, "リクエストの形式が不正です");
        // 固定メッセージで400 Bad Requestを返す（詳細な原因はクライアントに返さない）
        return build(HttpStatus.BAD_REQUEST, "リクエストの形式が不正です");
    }

    // docs/30_詳細設計/31_API詳細設計書.md「リクエスト形式エラー」: パスパラメータが数値でない場合等
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        warn(request, "パラメータの形式が不正です");
        return build(HttpStatus.BAD_REQUEST, "パラメータの形式が不正です");
    }

    // docs/30_詳細設計/33_共通詳細設計書.md E-V-028: 存在しないURLへのリクエスト。
    // ブラウザが自動で取得する/favicon.ico等でも発生するため、ログには出力しない（同 6.2）。
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "指定されたURLは存在しません");
    }

    // docs/30_詳細設計/33_共通詳細設計書.md E-V-029: URLは存在するが、対応していないHTTPメソッドでのリクエスト。
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "この操作には対応していません");
    }

    // docs/30_詳細設計/33_共通詳細設計書.md E-V-030: 本文の形式（Content-Type）がJSONでないリクエスト。
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "リクエストの形式が不正です");
    }

    // docs/30_詳細設計/33_共通詳細設計書.md E-S-001: 上記のいずれにも該当しない想定外の例外。
    // スタックトレースは画面には出さず、ログにのみ出力する（docs/30_詳細設計/33_共通詳細設計書.md）。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 他のハンドラーと違いERRORレベルでログ出力し、例外exそのもの（スタックトレース）も記録する
        log.error("E-S-001 想定外のエラー userId={} {} {}", currentUserIdOrDash(), request.getMethod(), request.getRequestURI(), ex);
        // クライアントにはスタックトレースを見せず、固定の文言で500 Internal Server Errorを返す
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "システムエラーが発生しました。時間をおいて再度お試しください");
    }

    // SpringのFieldError（field名とデフォルトメッセージを持つ）を、
    // このプロジェクト独自のErrorResponse.FieldErrorに変換するだけの小さな変換メソッド
    private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
        return new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    // 指定したHTTPステータス・メッセージからErrorResponseを組み立て、
    // ResponseEntity（ステータス＋本文）として返す共通処理
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message));
    }

    // docs/30_詳細設計/33_共通詳細設計書.md・11-3: 想定内だが注意が必要なエラー（業務例外・入力エラー系）のログ出力
    // userId・HTTPメソッド・パス・エラーメッセージをまとめてWARNレベルで出力する
    private void warn(HttpServletRequest request, String message) {
        log.warn("userId={} {} {} {}", currentUserIdOrDash(), request.getMethod(), request.getRequestURI(), message);
    }

    // docs/30_詳細設計/33_共通詳細設計書.md: 未ログインでのアクセス失敗時はuserId=-とする
    // AuthContextにログインユーザーがセットされていればそのuserIdを文字列化し、
    // セットされていなければ（未ログイン時）"-"を返す
    private String currentUserIdOrDash() {
        return authContext.getCurrentUser() == null ? "-" : String.valueOf(authContext.getCurrentUser().userId());
    }
}
