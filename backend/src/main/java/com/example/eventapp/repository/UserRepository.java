package com.example.eventapp.repository;

import com.example.eventapp.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。usersテーブルへの問い合わせ口。
// AuthInterceptorがX-User-Idヘッダの値からログインユーザーを引くのに使う。
public interface UserRepository extends JpaRepository<User, Long> {

    // 機能追加（軽い会員登録）: メールアドレスの重複チェック用
    boolean existsByEmail(String email);

    // 機能追加（メールアドレスでのログイン）
    Optional<User> findByEmail(String email);

    // 機能追加（管理者向けユーザー一覧、マスタ確認用）
    List<User> findAllByOrderByIdAsc();
}
