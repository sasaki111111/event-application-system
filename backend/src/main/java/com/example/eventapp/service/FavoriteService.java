package com.example.eventapp.service;

import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.FavoriteEventResponse;
import com.example.eventapp.dto.FavoriteResponse;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.Favorite;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.FavoriteRepository;
import com.example.eventapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。お気に入り登録・解除・一覧（AP-16〜18）の業務ロジック。
// 登録・解除はどちらも冪等：同じ状態への操作を繰り返してもエラーにしない。
/**
 * お気に入り登録（AP-16）・解除（AP-17）・一覧（AP-18）、お気に入り総数取得（AP-29）の業務ロジックを担当するService。
 * FavoriteController（add／remove／myFavorites／count）とUserController#favoritesOf（AP-28）から呼ばれ、
 * DBアクセスにはFavoriteRepository・EventRepository・ApplicationRepository・UserRepositoryを使う。
 */
@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    private final FavoriteRepository favoriteRepository;
    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, EventRepository eventRepository,
            ApplicationRepository applicationRepository, UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
    }

    // AP-16: 既に登録済みなら新規作成せず既存の1件をそのまま返す。
    // createdは新規作成か既存かを表し、ControllerがHTTPステータス（201／200）の出し分けに使う
    /**
     * イベントをお気に入り登録する（AP-16）。FavoriteController#addから呼ばれる。
     *
     * @param userId  登録する利用者ID
     * @param eventId 対象イベントID
     * @return 登録結果（登録内容＋新規作成かどうか）
     */
    @Transactional
    public FavoriteAddResult add(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        Optional<Favorite> existing = favoriteRepository.findByUser_IdAndEvent_Id(userId, eventId);
        boolean created = existing.isEmpty();
        // Optional.orElseGet(...): 値が入っていればそれをそのまま使い、空（未登録）の場合だけ
        // 引数のラムダ式（() -> ...）を実行してその結果を使う。orElseThrow()と似ているが、
        // 例外を投げる代わりに「新規に作って使う」という違いがある。
        Favorite favorite = existing.orElseGet(
                () -> favoriteRepository.save(new Favorite(userRepository.getReferenceById(userId), event)));

        if (created) {
            log.info("お気に入り登録完了 userId={} eventId={}", userId, eventId);
        }
        return new FavoriteAddResult(new FavoriteResponse(favorite.getId(), eventId, favorite.getCreatedAt()), created);
    }

    // AP-17: 未登録でもエラーにしない（冪等）
    /**
     * イベントのお気に入り登録を解除する（AP-17）。FavoriteController#removeから呼ばれる。
     *
     * @param userId  解除する利用者ID
     * @param eventId 対象イベントID
     */
    @Transactional
    public void remove(Long userId, Long eventId) {
        favoriteRepository.deleteByUser_IdAndEvent_Id(userId, eventId);
        log.info("お気に入り解除完了 userId={} eventId={}", userId, eventId);
    }

    // AP-18: 自分のお気に入り一覧（登録日時の降順）。ソフトデリート済みイベントも
    // 履歴としてそのまま表示する（一覧・詳細系APIのようなdeleted_atでの絞り込みは行わない）
    /**
     * 利用者のお気に入り一覧を取得する（AP-18）。FavoriteController#myFavoritesに加え、
     * UserController#favoritesOf（AP-28、管理者が他利用者を対象にする場合）からも共通で呼ばれる。
     *
     * @param userId 対象の利用者ID
     * @return お気に入り一覧（登録日時降順、削除済みイベントへの登録も含む）
     */
    @Transactional(readOnly = true)
    public List<FavoriteEventResponse> myFavorites(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return favoriteRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(favorite -> toFavoriteEventResponse(favorite, now))
                .toList();
    }

    // 管理者ダッシュボードのお気に入り総数（AP-29）
    /**
     * 管理者ダッシュボード（SC-07）向けの、お気に入り登録総数を取得する（AP-29）。FavoriteController#countから呼ばれる。
     *
     * @return お気に入り登録件数
     */
    @Transactional(readOnly = true)
    public long countAll() {
        return favoriteRepository.count();
    }

    private FavoriteEventResponse toFavoriteEventResponse(Favorite favorite, LocalDateTime now) {
        Event event = favorite.getEvent();
        long acceptedCount = applicationRepository.countByEvent_IdAndStatus(event.getId(), ApplicationStatus.ACCEPTED);
        return new FavoriteEventResponse(
                event.getId(),
                event.getName(),
                event.getStartAt(),
                event.getPlace(),
                event.getCapacity(),
                event.getApplicationDeadline(),
                acceptedCount,
                event.isOpen(now),
                event.getOrganizerName(),
                event.getImageUrl(),
                favorite.getCreatedAt()
        );
    }
}
