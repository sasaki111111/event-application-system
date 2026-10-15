package com.example.eventapp.repository;

import com.example.eventapp.entity.ApplicationStatusMaster;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。application_statusesテーブル（申込状況マスタ）への問い合わせ口。主キーはコード値
public interface ApplicationStatusRepository extends JpaRepository<ApplicationStatusMaster, Integer> {
}
