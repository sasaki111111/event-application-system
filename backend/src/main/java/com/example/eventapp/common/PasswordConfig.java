package com.example.eventapp.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * パスワードのハッシュ化・照合に使う部品をSpringに登録する設定クラス
 * （docs/20_基本設計/24_方式設計書.md 3.1）。Spring Securityのうち暗号化機能のみを使い、
 * 認証の枠組み全体（フィルタ等）は導入しない。
 */
// 実行環境: サーバー側（JVM）。BCryptによるパスワードのハッシュ化・照合部品（PasswordEncoder）をBeanとして登録する。
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: ハッシュ値から元のパスワードを復元できない方式。同じパスワードでも毎回異なるハッシュ値になる
        return new BCryptPasswordEncoder();
    }
}
