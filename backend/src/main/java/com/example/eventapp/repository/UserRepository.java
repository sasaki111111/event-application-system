package com.example.eventapp.repository;

import com.example.eventapp.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。usersテーブルへの問い合わせ口。
// AuthInterceptorがX-User-Idヘッダの値からログインユーザーを引くのに使う。
public interface UserRepository extends JpaRepository<User, Long> {

    // 機能追加（軽い会員登録）: メールアドレスの重複チェック用
    // email = ? の行が存在するかどうかを判定する
    boolean existsByEmail(String email);

    // 機能追加（メールアドレスでのログイン）
    // email = ? に一致する1件を取得する
    Optional<User> findByEmail(String email);

    // 機能追加（管理者向けユーザー一覧、マスタ確認用）
    // findAllByOrderByIdAsc = 全件取得＋ORDER BY id ASC に相当するクエリメソッド
    List<User> findAllByOrderByIdAsc();

    // AP-146: 管理者権限の降格で、対象が最後の1人の管理者でないかを判定するのに使う
    // role = ? の件数を数える
    long countByRoleCode(Integer roleCode);
}
