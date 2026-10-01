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
    @Transactional
    public FavoriteAddResult add(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        Optional<Favorite> existing = favoriteRepository.findByUser_IdAndEvent_Id(userId, eventId);
        boolean created = existing.isEmpty();
        Favorite favorite = existing.orElseGet(
                () -> favoriteRepository.save(new Favorite(userRepository.getReferenceById(userId), event)));

        if (created) {
            log.info("お気に入り登録完了 userId={} eventId={}", userId, eventId);
        }
        return new FavoriteAddResult(new FavoriteResponse(favorite.getId(), eventId, favorite.getCreatedAt()), created);
    }

    // AP-17: 未登録でもエラーにしない（冪等）
    @Transactional
    public void remove(Long userId, Long eventId) {
        favoriteRepository.deleteByUser_IdAndEvent_Id(userId, eventId);
        log.info("お気に入り解除完了 userId={} eventId={}", userId, eventId);
    }

    // AP-18: 自分のお気に入り一覧（登録日時の降順）。ソフトデリート済みイベントも
    // 履歴としてそのまま表示する（一覧・詳細系APIのようなdeleted_atでの絞り込みは行わない）
    @Transactional(readOnly = true)
    public List<FavoriteEventResponse> myFavorites(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return favoriteRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(favorite -> toFavoriteEventResponse(favorite, now))
                .toList();
    }

    // 管理者ダッシュボードのお気に入り総数（AP-29）
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
