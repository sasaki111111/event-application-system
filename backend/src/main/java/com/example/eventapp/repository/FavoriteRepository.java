package com.example.eventapp.repository;

import com.example.eventapp.entity.Favorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。favoritesテーブルへの問い合わせ口。
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // AP-16: 既に登録済みならそれをそのまま返す（冪等、要件定義書§8 E9）
    Optional<Favorite> findByUser_IdAndEvent_Id(Long userId, Long eventId);

    // AP-17: 解除。未登録でも0件削除で正常終了する（冪等）
    void deleteByUser_IdAndEvent_Id(Long userId, Long eventId);

    // AP-18: 自分のお気に入り一覧（登録日時の降順）
    List<Favorite> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
