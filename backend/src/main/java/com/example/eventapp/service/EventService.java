package com.example.eventapp.service;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.DeletedEventResponse;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベント一覧・詳細（D-1）、登録・編集・削除（D-2）の業務ロジック。
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;

    public EventService(EventRepository eventRepository, ApplicationRepository applicationRepository) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
    }

    // API-01: status=all(既定)は全件、status=openは申込受付中のみ（API設計書§2）
    @Transactional(readOnly = true)
    public List<EventSummaryResponse> list(String status) {
        LocalDateTime now = LocalDateTime.now();
        boolean openOnly = "open".equals(status);

        return eventRepository.findAllByDeletedAtIsNullOrderByStartAtAsc().stream()
                .filter(event -> !openOnly || event.isOpen(now))
                .map(event -> toSummary(event, now))
                .toList();
    }

    // 機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧（API-XX）
    @Transactional(readOnly = true)
    public List<DeletedEventResponse> listDeleted() {
        return eventRepository.findAllByDeletedAtIsNotNullOrderByStartAtAsc().stream()
                .map(event -> new DeletedEventResponse(
                        event.getId(),
                        event.getName(),
                        event.getStartAt(),
                        event.getPlace(),
                        event.getCapacity(),
                        event.getDeletedAt()
                ))
                .toList();
    }

    // API-02: 指定IDのイベントが無ければ404（API設計書§2）
    @Transactional(readOnly = true)
    public EventDetailResponse getDetail(Long id) {
        Event event = findByIdOrThrow(id);
        return toDetail(event);
    }

    // API-06: イベント登録（管理者のみ。権限チェックはController側）
    @Transactional
    public EventDetailResponse create(EventUpsertRequest request) {
        Event event = new Event(
                request.name(),
                request.startAt(),
                request.place(),
                request.capacity(),
                request.applicationDeadline(),
                request.description()
        );
        Event saved = eventRepository.save(event);
        return toDetail(saved);
    }

    // API-07: イベント編集。指定IDが無ければ404
    @Transactional
    public EventDetailResponse update(Long id, EventUpsertRequest request) {
        Event event = findByIdOrThrow(id);
        event.applyChanges(
                request.name(),
                request.startAt(),
                request.place(),
                request.capacity(),
                request.applicationDeadline(),
                request.description()
        );
        return toDetail(event);
    }

    // API-08: イベント削除。受付済の申込が1件でもあれば400、指定IDが無ければ404
    // 機能追加（ソフトデリート）: 物理削除ではなくdeleted_atを立てるのみ。「削除済みイベント」画面から復元できる。
    @Transactional
    public void delete(Long id) {
        Event event = findByIdOrThrow(id);
        if (countAccepted(event.getId()) > 0) {
            throw new BusinessException("申込があるため削除できません");
        }
        event.softDelete();
    }

    // 機能追加（ソフトデリートの復元）。削除済みでなければ404。
    @Transactional
    public EventDetailResponse restore(Long id) {
        Event event = eventRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new NotFoundException("削除済みイベントが見つかりません"));
        event.restore();
        return toDetail(event);
    }

    private Event findByIdOrThrow(Long id) {
        return eventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));
    }

    private EventSummaryResponse toSummary(Event event, LocalDateTime now) {
        return new EventSummaryResponse(
                event.getId(),
                event.getName(),
                event.getStartAt(),
                event.getPlace(),
                event.getCapacity(),
                event.getApplicationDeadline(),
                countAccepted(event.getId()),
                event.isOpen(now)
        );
    }

    private EventDetailResponse toDetail(Event event) {
        long acceptedCount = countAccepted(event.getId());
        return new EventDetailResponse(
                event.getId(),
                event.getName(),
                event.getStartAt(),
                event.getPlace(),
                event.getCapacity(),
                event.getApplicationDeadline(),
                acceptedCount,
                event.isOpen(LocalDateTime.now()),
                event.getDescription(),
                event.getCapacity() - acceptedCount
        );
    }

    private long countAccepted(Long eventId) {
        // 対象件数が小さい前提（要件定義書§2）のため、イベントごとに1クエリで数える簡潔な実装にしている
        return applicationRepository.countByEvent_IdAndStatus(eventId, ApplicationStatus.ACCEPTED);
    }
}
