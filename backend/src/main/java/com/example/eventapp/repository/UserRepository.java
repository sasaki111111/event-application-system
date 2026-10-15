package com.example.eventapp.repository;

import com.example.eventapp.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。usersテーブルへの問い合わせ口。
// AuthInterceptorがX-User-Idヘッダの値からログインユーザーを引くのに使う。
public interface UserRepository extends JpaRepository<User, Long> {

    // AP-011・AP-145: メールアドレスの重複チェック用（R-15）
    // email = ? の行が存在するかどうかを判定する
    boolean existsByEmail(String email);

    // AP-010: ログイン時にメールアドレスから利用者を検索する
    // email = ? に一致する1件を取得する
    Optional<User> findByEmail(String email);

    // AP-140: 利用者一覧（利用者ID昇順）
    // findAllByOrderByIdAsc = 全件取得＋ORDER BY id ASC に相当するクエリメソッド
    List<User> findAllByOrderByIdAsc();

    // AP-146: 管理者権限の降格で、対象が最後の1人の管理者でないかを判定するのに使う
    // role_code = ? の件数を数える（R-17）
    long countByRoleCode(Integer roleCode);
}
