package com.example.eventapp.repository;

import com.example.eventapp.entity.Event;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 実行環境: サーバー側（JVM）。eventsテーブルへの問い合わせ口。
public interface EventRepository extends JpaRepository<Event, Long> {

    // AP-04: GET /api/events は開催日時の昇順で固定（API設計書§0）。ソフトデリート済みは除外する。
    List<Event> findAllByDeletedAtIsNullOrderByStartAtAsc();

    // 機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧用
    List<Event> findAllByDeletedAtIsNotNullOrderByStartAtAsc();

    // 通常の参照・更新はソフトデリート済みを除外する
    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    // 機能追加（ソフトデリートの復元）: 削除済みイベントのみを対象に取得する
    Optional<Event> findByIdAndDeletedAtIsNotNull(Long id);

    // O-01: 同時申込時の排他制御（悲観ロック）。区分の無いイベントで、定員判定〜申込登録・
    // 繰り上げの間、対象イベント行をロックして直列化する（SELECT ... FOR UPDATE）。
    // ロックはあくまで直列化のための手段であり業務判定ではないため、deleted_atでの絞り込みは行わない
    // （ApplicationService側で別途、有効なイベントであることを確認済みの上で呼ぶ）。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
