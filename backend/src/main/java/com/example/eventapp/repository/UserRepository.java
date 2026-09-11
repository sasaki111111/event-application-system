package com.example.eventapp.repository;

import com.example.eventapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。usersテーブルへの問い合わせ口。
// AuthInterceptorがX-User-Idヘッダの値からログインユーザーを引くのに使う。
public interface UserRepository extends JpaRepository<User, Long> {
}
