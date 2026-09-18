package com.example.eventapp.repository;

import com.example.eventapp.entity.Application;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。applicationsテーブルへの問い合わせ口。
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // 受付済数の集計（acceptedCount／充足率）に使う。テーブル定義書§5「カラムにしない派生値」
    long countByEvent_IdAndStatus(Long eventId, String status);

    // 区分単位の受付済数の集計（区分ありイベントのticketTypes[].acceptedCount、定員判定にも使用）
    long countByTicketType_IdAndStatus(Long ticketTypeId, String status);

    // イベント保存時の区分全置換ロジックで、区分に紐づく申込（受付済・キャンセル待ち）が
    // 残っていないかを確認するために使う（残っている場合は区分の変更を拒否する）
    boolean existsByEvent_IdAndTicketTypeIsNotNullAndStatusIn(Long eventId, Collection<String> statuses);

    // D-3: 二重申込チェック（同一ユーザー×同一イベントに「受付済」が既に無いか）
    boolean existsByUser_IdAndEvent_IdAndStatus(Long userId, Long eventId, String status);

    // 機能追加（キャンセル待ち）: 二重申込チェックを受付済・キャンセル待ちの両方に対して行う
    boolean existsByUser_IdAndEvent_IdAndStatusIn(Long userId, Long eventId, Collection<String> statuses);

    // 機能追加（キャンセル待ちの繰り上げ）: 対象イベントで最も古いキャンセル待ちを1件取得する
    Optional<Application> findFirstByEvent_IdAndStatusOrderByAppliedAtAsc(Long eventId, String status);

    // API-04: 自分の申込一覧（申込日時の降順、テーブル定義書のidx_app_user_appliedを使う想定）
    List<Application> findByUser_IdOrderByAppliedAtDesc(Long userId);

    // API-09 format=csv: 申込実績の明細一覧（開催日時順）。ステータス問わず全件（受付済・キャンセル済とも実績として出す）
    List<Application> findAllByOrderByEvent_StartAtAsc();
}
