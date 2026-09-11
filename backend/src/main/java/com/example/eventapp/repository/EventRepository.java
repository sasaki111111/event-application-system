package com.example.eventapp.repository;

import com.example.eventapp.entity.Event;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。eventsテーブルへの問い合わせ口。
public interface EventRepository extends JpaRepository<Event, Long> {

    // API-01: GET /api/events は開催日時の昇順で固定（API設計書§0）
    List<Event> findAllByOrderByStartAtAsc();
}
