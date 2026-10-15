package com.example.eventapp.repository;

import com.example.eventapp.entity.Application;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// repositoryパッケージの各インターフェースは、Spring Data JPAという仕組みを使っている。
// JpaRepository<Entity型, ID型> を継承するだけで、実装クラス（中身の処理）を自分で書かなくても、
// 登録・1件取得・全件取得・更新・削除といった基本的なCRUD操作のメソッドが自動的に使えるようになる
// （実際の実装はSpring Data JPAがアプリ起動時に自動生成する）。
// さらに、このインターフェースに自分でメソッドを宣言するだけで、メソッド名から対応するSQLを
// Spring Data JPAが自動的に組み立ててくれる「クエリメソッド」という機能がある。例えば
// `findByUser_IdOrderByAppliedAtDesc` は「findBy（検索）＋User_Id（userカラムのidで絞り込み）＋
// OrderByAppliedAtDesc（appliedAtの降順に並べる）」という命名規則の通りに解釈され、
// `WHERE user_id = ? ORDER BY applied_at DESC` に相当するSQLが自動生成される。
// 命名規則だけでは表現できない複雑な条件のときは、メソッドに`@Query`を付けてJPQL
// （JPA独自のSQLに似た言い回し。エンティティ名・プロパティ名を使う）を直接書くこともできる
// （EventRepositoryのfindByIdForUpdate()等を参照）。
// 実行環境: サーバー側（JVM）。applicationsテーブルへの問い合わせ口。
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // 受付済数の集計（acceptedCount／充足率）に使う。集計値はカラムに持たず都度数える
    // （docs/20_基本設計/22_テーブル定義書.md 6章「その他の方針」）
    // countByEvent_IdAndStatus = 「event_id=? AND status=? の件数」に相当するSQLが自動生成される
    long countByEvent_IdAndStatus(Long eventId, Integer status);

    // 区分単位の受付済数の集計（区分ありイベントのticketTypes[].acceptedCount、定員判定にも使用）
    long countByTicketType_IdAndStatus(Long ticketTypeId, Integer status);

    // イベント保存時の区分全置換ロジックで、対象イベントに申込（受付済・キャンセル待ち）が
    // 残っていないかを確認するために使う（残っている場合は区分の変更を拒否する）。
    // ticketTypeが無い申込（区分の無いイベントだった時点の申込）も対象に含める
    // existsBy... = 「1件でも存在すればtrue」に相当するSQL（EXISTS）が自動生成される
    boolean existsByEvent_IdAndStatusIn(Long eventId, Collection<Integer> statuses);

    // 同一ユーザー×同一イベントに、指定した状況の申込があるか。現在は呼び出し元が無い
    // （二重申込チェックは、受付済・キャンセル待ちの両方を見る下のexistsByUser_IdAndEvent_IdAndStatusInを使う）
    boolean existsByUser_IdAndEvent_IdAndStatus(Long userId, Long eventId, Integer status);

    // 二重申込チェック（R-03）: 受付済・キャンセル待ちの両方に対して行う
    boolean existsByUser_IdAndEvent_IdAndStatusIn(Long userId, Long eventId, Collection<Integer> statuses);

    // キャンセル待ちの繰り上げ（R-05、区分の無いイベント）: 対象イベントで最も古いキャンセル待ちを1件取得する
    // findFirstBy...OrderBy...Asc = 条件に合う行を昇順に並べた上で先頭1件だけを取得するSQLが自動生成される
    Optional<Application> findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(Long eventId, Integer status);

    // キャンセル待ちの繰り上げ（R-05、区分単位）: 対象区分で最も古いキャンセル待ちを1件取得する
    Optional<Application> findFirstByTicketType_IdAndStatusOrderByAppliedAtAsc(Long ticketTypeId, Integer status);

    // キャンセル待ちの順位計算（AP-031のwaitlistRank、区分の無いイベント）: 自分より申込日時が古いキャンセル待ちの件数
    long countByEvent_IdAndTicketTypeIsNullAndStatusAndAppliedAtLessThan(Long eventId, Integer status, LocalDateTime appliedAt);

    // キャンセル待ちの順位計算（AP-031のwaitlistRank、区分単位）: 自分より申込日時が古いキャンセル待ちの件数
    long countByTicketType_IdAndStatusAndAppliedAtLessThan(Long ticketTypeId, Integer status, LocalDateTime appliedAt);

    // AP-031・AP-142: 申込一覧（申込日時の降順、テーブル定義書8章のidx_app_user_appliedを使う想定）
    List<Application> findByUser_IdOrderByAppliedAtDesc(Long userId);

    // AP-125: 当日受付の申込者一覧（申込日時の昇順）
    List<Application> findByEvent_IdOrderByAppliedAtAsc(Long eventId);

    // AP-130 format=csv: 申込実績の明細一覧（開催日時順）。ステータス問わず全件が対象だが、
    // 削除済みイベントに紐づく申込は対象外とする（集計一覧＝summarize()と対象範囲を揃える）
    // Event_DeletedAtIsNull = 関連するEventエンティティのdeletedAtがNULL（未削除）という条件を、
    // テーブルを結合（JOIN）して絞り込むSQLが自動生成される
    List<Application> findAllByEvent_DeletedAtIsNullOrderByEvent_StartAtAsc();
}
