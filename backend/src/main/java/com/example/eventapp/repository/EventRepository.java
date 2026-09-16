package com.example.eventapp.repository;

import com.example.eventapp.entity.Event;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。eventsテーブルへの問い合わせ口。
public interface EventRepository extends JpaRepository<Event, Long> {

    // API-01: GET /api/events は開催日時の昇順で固定（API設計書§0）。ソフトデリート済みは除外する。
    List<Event> findAllByDeletedAtIsNullOrderByStartAtAsc();

    // 機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧用
    List<Event> findAllByDeletedAtIsNotNullOrderByStartAtAsc();

    // 通常の参照・更新はソフトデリート済みを除外する
    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    // 機能追加（ソフトデリートの復元）: 削除済みイベントのみを対象に取得する
    Optional<Event> findByIdAndDeletedAtIsNotNull(Long id);
}
