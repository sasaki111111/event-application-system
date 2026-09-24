package com.example.eventapp.service;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.DeletedEventResponse;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.dto.TicketTypeRequest;
import com.example.eventapp.dto.TicketTypeResponse;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.TicketType;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.TicketTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベント一覧・詳細（D-1）、登録・編集・削除（D-2）の業務ロジック。
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final TicketTypeRepository ticketTypeRepository;

    public EventService(EventRepository eventRepository, ApplicationRepository applicationRepository,
            TicketTypeRepository ticketTypeRepository) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.ticketTypeRepository = ticketTypeRepository;
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

    // 機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧（API-13）
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
                request.description(),
                request.organizerName(),
                request.imageUrl(),
                request.category(),
                request.extraQuestion()
        );
        Event saved = eventRepository.save(event);
        List<TicketType> ticketTypes = saveTicketTypes(saved, request.ticketTypes());
        return toDetail(saved, ticketTypes);
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
                request.description(),
                request.organizerName(),
                request.imageUrl(),
                request.category(),
                request.extraQuestion()
        );
        List<TicketType> ticketTypes = saveTicketTypes(event, request.ticketTypes());
        return toDetail(event, ticketTypes);
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

    // 機能追加（定員区分）: 区分の全置換。requestsがnull＝区分の指定なし（既存の区分に手を加えない）。
    // 戻り値は更新後の区分一覧（呼び出し側がtoDetail()で再度クエリしなくて済むように）。
    // 既存の区分に受付済・キャンセル待ちの申込が残っている場合は変更を拒否する（詳細設計書_v2.0.md§3.3.10参照）。
    private List<TicketType> saveTicketTypes(Event event, List<TicketTypeRequest> requests) {
        if (requests == null) {
            // 区分を変更しない場合でも、既存の区分があるイベントはcapacityを区分の合計に保つ
            // （テーブル定義書_v2.0.md§2.2「区分がある場合はcapacityは区分の合計」との矛盾を防ぐ。
            // リクエストのcapacityは区分の無いイベントの場合のみ有効に使われる）
            List<TicketType> existing = ticketTypeRepository.findByEvent_Id(event.getId());
            if (!existing.isEmpty()) {
                event.syncCapacityFromTicketTypes(existing.stream().mapToInt(TicketType::getCapacity).sum());
            }
            return existing;
        }
        if (applicationRepository.existsByEvent_IdAndTicketTypeIsNotNullAndStatusIn(
                event.getId(), ApplicationStatus.ACTIVE_STATUSES)) {
            throw new BusinessException("区分に申込があるため変更できません");
        }
        try {
            ticketTypeRepository.deleteByEvent_Id(event.getId());
        } catch (DataIntegrityViolationException e) {
            // 上のチェックは受付済・キャンセル待ちのみを見ているため、キャンセル済の申込が
            // 区分を参照したまま残っているケースはここで拾う（fk_applications_ticket_typeはRESTRICT）
            throw new BusinessException("区分に申込の履歴が残っているため変更できません");
        }
        if (requests.isEmpty()) {
            return List.of();
        }
        List<TicketType> saved = requests.stream()
                .map(request -> ticketTypeRepository.save(new TicketType(event, request.name(), request.capacity())))
                .toList();
        event.syncCapacityFromTicketTypes(saved.stream().mapToInt(TicketType::getCapacity).sum());
        return saved;
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
                event.isOpen(now),
                event.getOrganizerName(),
                event.getImageUrl(),
                event.getCategory()
        );
    }

    private EventDetailResponse toDetail(Event event) {
        return toDetail(event, ticketTypeRepository.findByEvent_Id(event.getId()));
    }

    private EventDetailResponse toDetail(Event event, List<TicketType> ticketTypeEntities) {
        long acceptedCount = countAccepted(event.getId());
        List<TicketTypeResponse> ticketTypes = ticketTypeEntities.stream()
                .map(this::toTicketTypeResponse)
                .toList();
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
                event.getCapacity() - acceptedCount,
                event.getOrganizerName(),
                event.getImageUrl(),
                event.getCategory(),
                event.getExtraQuestion(),
                ticketTypes
        );
    }

    private TicketTypeResponse toTicketTypeResponse(TicketType ticketType) {
        long accepted = applicationRepository.countByTicketType_IdAndStatus(ticketType.getId(), ApplicationStatus.ACCEPTED);
        return new TicketTypeResponse(
                ticketType.getId(),
                ticketType.getName(),
                ticketType.getCapacity(),
                accepted,
                ticketType.getCapacity() - accepted
        );
    }

    private long countAccepted(Long eventId) {
        // 対象件数が小さい前提（要件定義書§2）のため、イベントごとに1クエリで数える簡潔な実装にしている
        return applicationRepository.countByEvent_IdAndStatus(eventId, ApplicationStatus.ACCEPTED);
    }
}
