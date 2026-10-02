package com.example.eventapp.common;

import com.example.eventapp.common.exception.ForbiddenException;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

// commonパッケージの AuthContext / AuthInterceptor / CurrentUser / WebConfig は、
// このプロジェクト独自の「ログイン中ユーザーを特定する仕組み（ダミー認証）」を構成する4クラス。
// 本物のパスワード認証画面は無く、HTTPリクエストヘッダ（X-User-Id）に入っているユーザーIDを
// そのまま信用する簡易な仕組みになっている（仕組みの詳細はAuthInterceptorのコメントを参照）。
/**
 * リクエスト単位で「今ログインしているのは誰か」を保持する入れ物（クラス）。
 * {@code @Scope(SCOPE_REQUEST)}が付いているため、HTTPリクエスト1回ごとに新しいインスタンスが
 * 作られる（＝別のリクエストの情報と混ざらない）。AuthInterceptorが{@code setCurrentUser}で
 * ログインユーザー情報をセットし、その後Controller/ServiceがDI（依存性の注入、Springが
 * 必要なインスタンスを自動で渡してくれる仕組み）でこのクラスを受け取り、{@code getCurrentUser}で
 * 参照する「バトンリレー」の中継役。
 */
// 実行環境: サーバー側（JVM）。HTTPリクエスト1回ごとに1つ生成され（@Scope=request）、
// AuthInterceptorが設定したログインユーザー情報をController/Serviceから参照できるようにする「バトンリレー」役。
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AuthContext {

    private CurrentUser currentUser;

    public CurrentUser getCurrentUser() {
        // フィールドに保持しているCurrentUser（未認証ならnull）をそのまま返す
        return currentUser;
    }

    public void setCurrentUser(CurrentUser currentUser) {
        // AuthInterceptorから渡されたCurrentUserをフィールドに保持する
        this.currentUser = currentUser;
    }

    /**
     * 管理者専用APIの権限チェック。ログイン中ユーザーが管理者でなければ{@code ForbiddenException}
     * （HTTP 403 Forbidden）を投げる。各Controllerに同じチェック処理が重複していたため、
     * ここに1つにまとめてある。
     */
    // 管理者専用APIの権限チェック（各Controllerに重複していたものを集約）
    public void requireAdmin() {
        // ログイン中ユーザーのroleが"admin"かどうかをCurrentUser#isAdminで判定し、
        // 管理者でなければForbiddenException（403）を投げて処理を中断する
        if (!currentUser.isAdmin()) {
            throw new ForbiddenException("権限がありません");
        }
    }
}
