package com.example.eventapp.common;

import com.example.eventapp.common.exception.ForbiddenException;
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

    // 管理者専用APIの権限チェック（各Controllerに重複していたものを集約）
    public void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new ForbiddenException("権限がありません");
        }
    }
}
