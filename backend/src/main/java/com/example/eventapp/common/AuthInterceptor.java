package com.example.eventapp.common;

import com.example.eventapp.common.exception.UnauthorizedException;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// 実行環境: サーバー側（JVM）。Controllerの処理が始まる直前に必ず通る「関所」（HandlerInterceptor）。
// API設計書§0: X-User-Idヘッダからログインユーザーを解決するダミー認証。
// ヘッダ無し／存在しないuserIdは401（GlobalExceptionHandlerが変換）。
// どのURLに適用するか（/api/**、ただしlogin/pingは除外）はWebConfigで設定している。
// D-1: C-2で投入したusersテーブルをUserRepository経由で見るようにした（B-5時点のDummyUserStoreを置き換え）。
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-User-Id";

    private final UserRepository userRepository;
    private final AuthContext authContext;

    public AuthInterceptor(UserRepository userRepository, AuthContext authContext) {
        this.userRepository = userRepository;
        this.authContext = authContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            // CORSプリフライトリクエストは認証ヘッダを付けずに送られるため素通りさせる
            return true;
        }

        // 軽い会員登録（機能追加）: POST /api/usersはログイン前に呼ばれるため認証不要。
        // GET /api/users（ユーザー一覧、管理者専用）はこの対象外＝通常通り認証・権限チェックされる。
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/users".equals(request.getRequestURI())) {
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

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        authContext.setCurrentUser(new CurrentUser(user.getId(), user.getName(), user.getRole()));
        return true;
    }
}
