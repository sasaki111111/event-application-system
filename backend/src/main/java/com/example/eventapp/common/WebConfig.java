package com.example.eventapp.common;

import com.example.eventapp.repository.UserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 実行環境: サーバー側（JVM）。Spring MVCの共通設定をまとめる場所。
// ここで各種HandlerInterceptorをどのURLに適用するかと、CORS（ブラウザの別オリジンからのアクセス許可）を設定する。
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserRepository userRepository;
    private final AuthContext authContext;

    public WebConfig(UserRepository userRepository, AuthContext authContext) {
        this.userRepository = userRepository;
        this.authContext = authContext;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 回数制限（docs/10_非機能設計書.md 10-3、機能追加）は認証の有無によらず最初に適用する
        // （未認証でのログイン総当たり・登録スパムも抑止対象のため）。
        registry.addInterceptor(new RateLimitInterceptor())
                .addPathPatterns("/api/**");

        // ログイン・ping（起動確認用）は認証不要（docs/03_API設計書.md 2.1節）。
        // POST /api/users（軽い会員登録、機能追加）はAuthInterceptor側でメソッド単位に個別許可している
        // （GET /api/usersは一覧＝管理者専用のため、パス単位でここに含めるとGETまで無認証になってしまう）。
        registry.addInterceptor(new AuthInterceptor(userRepository, authContext))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login", "/api/ping");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // ローカル開発時のAngular devサーバー（ng serve、既定4200番）からのアクセスを許可
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
