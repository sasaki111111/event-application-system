package com.example.eventapp.common;

// 実行環境: サーバー側（JVM）。ログイン中ユーザー1人分の情報を表す入れ物（record＝フィールドのみのクラス）。
// AuthInterceptorが作り、AuthContext経由でController/Serviceに渡される。
public record CurrentUser(Long userId, String name, String role) {

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
