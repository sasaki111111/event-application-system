package com.example.eventapp.common;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

// 実行環境: サーバー側（JVM）。HTTPリクエスト1回ごとに1つ生成され（@Scope=request）、
// AuthInterceptorが設定したログインユーザー情報をController/Serviceから参照できるようにする「バトンリレー」役。
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AuthContext {

    private CurrentUser currentUser;

    public CurrentUser getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }
}
