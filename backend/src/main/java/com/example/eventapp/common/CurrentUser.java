package com.example.eventapp.common;

/**
 * ログイン中ユーザー1人分の情報を表す入れ物。Javaの{@code record}は、フィールド（ここでは
 * userId・name・role）とそれを取得するメソッドだけを自動生成してくれる、データを保持するための
 * 専用のクラス定義方法。AuthInterceptorが認証成功時にこのインスタンスを作り、AuthContext経由で
 * Controller/Serviceに渡される。
 */
// 実行環境: サーバー側（JVM）。ログイン中ユーザー1人分の情報を表す入れ物（record＝フィールドのみのクラス）。
// AuthInterceptorが作り、AuthContext経由でController/Serviceに渡される。
public record CurrentUser(Long userId, String name, String role) {

    // roleが"admin"文字列と一致するかどうかで管理者判定を行う
    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
