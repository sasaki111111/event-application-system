package com.example.eventapp.common;

import com.example.eventapp.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// API設計書§0: X-User-Idヘッダからログインユーザーを解決するダミー認証。
// ヘッダ無し／存在しないuserIdは401（GlobalExceptionHandlerが変換）。
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-User-Id";

    private final DummyUserStore userStore;
    private final AuthContext authContext;

    public AuthInterceptor(DummyUserStore userStore, AuthContext authContext) {
        this.userStore = userStore;
        this.authContext = authContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            // CORSプリフライトリクエストは認証ヘッダを付けずに送られるため素通りさせる
            return true;
        }

        String header = request.getHeader(HEADER_NAME);
        if (header == null || header.isBlank()) {
            throw new UnauthorizedException("認証が必要です");
        }

        Long userId;
        try {
            userId = Long.valueOf(header.trim());
        } catch (NumberFormatException ex) {
            throw new UnauthorizedException("認証が必要です");
        }

        CurrentUser user = userStore.findById(userId);
        if (user == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        authContext.setCurrentUser(user);
        return true;
    }
}
