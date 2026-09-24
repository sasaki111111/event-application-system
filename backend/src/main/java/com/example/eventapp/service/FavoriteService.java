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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。お気に入り登録・解除・一覧（機能17、API-15〜17）の業務ロジック。
// 登録・解除はどちらも冪等（要件定義書§8 E9）：同じ状態への操作を繰り返してもエラーにしない。
@Service
public class FavoriteService {

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

    // API-15: 既に登録済みなら新規作成せず既存の1件をそのまま返す
    @Transactional
    public FavoriteResponse add(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        Favorite favorite = favoriteRepository.findByUser_IdAndEvent_Id(userId, eventId)
                .orElseGet(() -> favoriteRepository.save(new Favorite(userRepository.getReferenceById(userId), event)));

        return new FavoriteResponse(favorite.getId(), eventId, favorite.getCreatedAt());
    }

    // API-15: 新規登録（201）と既存返却（200）をControllerで出し分けるための事前判定
    @Transactional(readOnly = true)
    public boolean isFavorited(Long userId, Long eventId) {
        return favoriteRepository.existsByUser_IdAndEvent_Id(userId, eventId);
    }

    // API-16: 未登録でもエラーにしない（冪等）
    @Transactional
    public void remove(Long userId, Long eventId) {
        favoriteRepository.deleteByUser_IdAndEvent_Id(userId, eventId);
    }

    // API-17: 自分のお気に入り一覧（登録日時の降順）。ソフトデリート済みイベントも
    // 履歴としてそのまま表示する（一覧・詳細系APIのようなdeleted_atでの絞り込みは行わない）
    @Transactional(readOnly = true)
    public List<FavoriteEventResponse> myFavorites(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return favoriteRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(favorite -> toFavoriteEventResponse(favorite, now))
                .toList();
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
                event.getCategory(),
                favorite.getCreatedAt()
        );
    }
}
