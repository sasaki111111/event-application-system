package com.example.eventapp.common;

import com.example.eventapp.repository.UserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 実行環境: サーバー側（JVM）。Spring MVCの共通設定をまとめる場所。
// ここで「AuthInterceptorをどのURLに適用するか」と「CORS（ブラウザの別オリジンからのアクセス許可）」を設定する。
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
        // API-10（ログイン）とping（起動確認用）は認証不要（API設計書§0・§2）
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
