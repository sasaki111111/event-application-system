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

    // AP-04: GET /api/events は開催日時の昇順で固定（docs/03_API設計書.md AP-04）。ソフトデリート済みは除外する。
    // DeletedAtIsNull = deleted_at IS NULL（未削除）、OrderByStartAtAsc = ORDER BY start_at ASC
    List<Event> findAllByDeletedAtIsNullOrderByStartAtAsc();

    // 機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧用
    // DeletedAtIsNotNull = deleted_at IS NOT NULL（削除済み）
    List<Event> findAllByDeletedAtIsNotNullOrderByStartAtAsc();

    // 通常の参照・更新はソフトデリート済みを除外する
    // id = ? AND deleted_at IS NULL に相当する1件検索
    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    // 機能追加（ソフトデリートの復元）: 削除済みイベントのみを対象に取得する
    Optional<Event> findByIdAndDeletedAtIsNotNull(Long id);

    // 同時申込時の排他制御（悲観ロック）。区分の無いイベントで、定員判定〜申込登録・
    // 繰り上げの間、対象イベント行をロックして直列化する（SELECT ... FOR UPDATE）。
    // ロックはあくまで直列化のための手段であり業務判定ではないため、deleted_atでの絞り込みは行わない
    // （ApplicationService側で別途、有効なイベントであることを確認済みの上で呼ぶ）。
    // 技術的な補足:
    // ・@Query("select e from Event e where e.id = :id") は、メソッド名の命名規則だけでは書けない
    //   （ロック付きの）問い合わせを指定するために、JPQL（JPA独自のクエリ言語。SQLに似ているが
    //   テーブル名・カラム名ではなくエンティティ名・プロパティ名を書く）を直接書いている例。
    //   `:id`はプレースホルダーで、引数に付けた@Param("id")の値が渡される。
    // ・@Lock(LockModeType.PESSIMISTIC_WRITE) は「悲観的ロック」と呼ばれる仕組みで、この問い合わせで
    //   取得した行を、このトランザクションが終わるまで他のトランザクションが同時に更新できないようにする
    //   （SELECT ... FOR UPDATEが発行される）。これにより、複数人が同時に同じイベントへ申込しても、
    //   定員チェックと申込登録が1件ずつ順番に処理される。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
