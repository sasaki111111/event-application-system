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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// 実行環境: サーバー側（JVM）。@RestControllerAdvice＝全Controllerで共通の例外ハンドラー。
// Service/Controllerが投げた例外を捕まえて、docs/03_API設計書.md 2.3節で決めたJSON形式
// （timestamp/status/error/message[/errors]）に変換して返す。
// 認証・権限・業務ルール違反・入力エラー系の例外はいずれもここに集約されるため、
// docs/11_ログ設計書.md 11-2のWARNログ（想定内だが注意が必要なとき）もここでまとめて出力する。
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuthContext authContext;

    public GlobalExceptionHandler(AuthContext authContext) {
        this.authContext = authContext;
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        warn(request, ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        warn(request, ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        warn(request, ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        warn(request, ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        warn(request, "入力エラー " + errors);
        ErrorResponse body = ErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), "入力エラー", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // 権限チェックの後に手動実行するバリデーション（@Validを使うと権限チェックより先に走ってしまうため）
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        warn(request, "入力エラー " + errors);
        ErrorResponse body = ErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), "入力エラー", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // docs/03_API設計書.md 2.4節「リクエスト形式エラー」: 不正なJSON・型不一致・必須ボディ欠落
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        warn(request, "リクエストの形式が不正です");
        return build(HttpStatus.BAD_REQUEST, "リクエストの形式が不正です");
    }

    // docs/03_API設計書.md 2.4節「リクエスト形式エラー」: パスパラメータが数値でない場合等
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        warn(request, "パラメータの形式が不正です");
        return build(HttpStatus.BAD_REQUEST, "パラメータの形式が不正です");
    }

    // docs/08_エラー設計書.md E-S-001: 上記のいずれにも該当しない想定外の例外。
    // スタックトレースは画面には出さず、ログにのみ出力する（docs/11_ログ設計書.md 11-6）。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("E-S-001 想定外のエラー userId={} {} {}", currentUserIdOrDash(), request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "システムエラーが発生しました。時間をおいて再度お試しください");
    }

    private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
        return new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message));
    }

    // docs/11_ログ設計書.md 11-2・11-3: 想定内だが注意が必要なエラー（業務例外・入力エラー系）のログ出力
    private void warn(HttpServletRequest request, String message) {
        log.warn("userId={} {} {} {}", currentUserIdOrDash(), request.getMethod(), request.getRequestURI(), message);
    }

    // docs/11_ログ設計書.md 11-3: 未ログインでのアクセス失敗時はuserId=-とする
    private String currentUserIdOrDash() {
        return authContext.getCurrentUser() == null ? "-" : String.valueOf(authContext.getCurrentUser().userId());
    }
}
