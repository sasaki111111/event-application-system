package com.example.eventapp.repository;

import com.example.eventapp.entity.TicketType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 実行環境: サーバー側（JVM）。ticket_typesテーブルへの問い合わせ口。
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    // イベント詳細・編集フォームの区分一覧表示用
    List<TicketType> findByEvent_Id(Long eventId);

    // 申込時の区分必須チェック（対象イベントに区分が1件以上あるか、要件定義書E12）。
    // findByEvent_Id()で全件取得してisEmpty()を見るより軽い
    boolean existsByEvent_Id(Long eventId);

    // イベント保存時の区分全置換ロジックで、既存の区分をまとめて削除するために使う
    void deleteByEvent_Id(Long eventId);

    // O-01: 同時申込時の排他制御（悲観ロック）。区分ありイベントで、定員判定〜申込登録・繰り上げの間、
    // 対象区分行をロックして直列化する（SELECT ... FOR UPDATE）。対象イベントに属する区分かの検証も兼ねる。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id = :id and t.event.id = :eventId")
    Optional<TicketType> findByIdAndEvent_IdForUpdate(@Param("id") Long id, @Param("eventId") Long eventId);

    // O-01: 同上。申込キャンセル時の繰り上げ（対象区分は判明済みなので、event_idでの検証は不要）用
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findByIdForUpdate(@Param("id") Long id);
}
