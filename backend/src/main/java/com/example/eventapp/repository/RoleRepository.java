package com.example.eventapp.repository;

import com.example.eventapp.entity.RoleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。rolesテーブル（利用者区分マスタ）への問い合わせ口。主キーはコード値
public interface RoleRepository extends JpaRepository<RoleMaster, Integer> {
}
