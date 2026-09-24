package com.example.eventapp.repository;

import com.example.eventapp.entity.TicketType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。ticket_typesテーブルへの問い合わせ口。
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    // イベント詳細・編集フォームの区分一覧表示用
    List<TicketType> findByEvent_Id(Long eventId);

    // 申込時の区分必須チェック（対象イベントに区分が1件以上あるか、要件定義書E12）。
    // findByEvent_Id()で全件取得してisEmpty()を見るより軽い
    boolean existsByEvent_Id(Long eventId);

    // 申込時の区分存在チェック（対象イベントに属する区分か、要件定義書E11）
    Optional<TicketType> findByIdAndEvent_Id(Long id, Long eventId);

    // イベント保存時の区分全置換ロジックで、既存の区分をまとめて削除するために使う
    void deleteByEvent_Id(Long eventId);
}
