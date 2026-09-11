package com.example.eventapp.service;

import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベント一覧・詳細の業務ロジック（D-1）。
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

        return eventRepository.findAllByOrderByStartAtAsc().stream()
                .filter(event -> !openOnly || event.isOpen(now))
                .map(event -> toSummary(event, now))
                .toList();
    }

    // API-02: 指定IDのイベントが無ければ404（API設計書§2）
    @Transactional(readOnly = true)
    public EventDetailResponse getDetail(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

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

    private long countAccepted(Long eventId) {
        // 対象件数が小さい前提（要件定義書§2）のため、イベントごとに1クエリで数える簡潔な実装にしている
        return applicationRepository.countByEvent_IdAndStatus(eventId, ApplicationStatus.ACCEPTED);
    }
}
