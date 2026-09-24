package com.example.eventapp.repository;

import com.example.eventapp.entity.EventComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。event_commentsテーブルへの問い合わせ口。
public interface EventCommentRepository extends JpaRepository<EventComment, Long> {

    // API-20: イベント詳細のコメント一覧（投稿日時の昇順、テーブル定義書のidx_event_comments_event_createdを使う想定）
    List<EventComment> findByEvent_IdOrderByCreatedAtAsc(Long eventId);
}
