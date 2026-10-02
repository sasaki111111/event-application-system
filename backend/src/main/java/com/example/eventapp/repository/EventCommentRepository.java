package com.example.eventapp.repository;

import com.example.eventapp.entity.EventComment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。event_commentsテーブルへの問い合わせ口。
public interface EventCommentRepository extends JpaRepository<EventComment, Long> {

    // AP-19: イベント詳細のコメント一覧（投稿日時の昇順、テーブル定義書のidx_event_comments_event_createdを使う想定）。
    // created_atは秒単位のため、同一秒内の投稿順が不定にならないようidを第2キーにする
    // OrderByCreatedAtAscIdAsc = ORDER BY created_at ASC, id ASC（第1キー・第2キーの順に並べる）に相当
    List<EventComment> findByEvent_IdOrderByCreatedAtAscIdAsc(Long eventId);

    // 返信投稿時、返信先が同一イベントのコメントであることを検証するために使う
    // id = ? AND event_id = ? の両方に一致する1件を取得する
    Optional<EventComment> findByIdAndEvent_Id(Long id, Long eventId);

    // 削除可否判定（返信が1件も無ければ物理削除、1件以上あれば論理削除）に使う
    // parent_comment_id = ? の行が1件でも存在するかを判定する
    boolean existsByParentComment_Id(Long parentCommentId);

    // 管理者ダッシュボードのコメント総数（論理削除済みは除外、AP-30）
    // deleted_at IS NULL の件数を数える
    long countByDeletedAtIsNull();

    // 利用者詳細（SC-15）のコメント履歴（AP-31、投稿日時降順。論理削除済みも含む）
    List<EventComment> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    // 全コメント一覧（SC-16コメントモデレーション、AP-32、有効なコメントのみ・投稿日時降順）
    List<EventComment> findByDeletedAtIsNullOrderByCreatedAtDesc();
}
