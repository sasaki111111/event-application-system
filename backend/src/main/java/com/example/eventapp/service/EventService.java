package com.example.eventapp.service;

import com.example.eventapp.common.AuthContext;
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
import com.example.eventapp.repository.FavoriteRepository;
import com.example.eventapp.repository.TicketTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベント一覧・詳細、登録・編集・削除・復元の業務ロジック。
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final FavoriteRepository favoriteRepository;
    private final AuthContext authContext;

    public EventService(EventRepository eventRepository, ApplicationRepository applicationRepository,
            TicketTypeRepository ticketTypeRepository, FavoriteRepository favoriteRepository,
            AuthContext authContext) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.favoriteRepository = favoriteRepository;
        this.authContext = authContext;
    }

    // AP-04: status=all(既定)は全件、status=openは申込受付中のみ（API設計書§2）
    @Transactional(readOnly = true)
    public List<EventSummaryResponse> list(String status) {
        LocalDateTime now = LocalDateTime.now();
        boolean openOnly = "open".equals(status);

        return eventRepository.findAllByDeletedAtIsNullOrderByStartAtAsc().stream()
                .filter(event -> !openOnly || event.isOpen(now))
                .map(event -> toSummary(event, now))
                .toList();
    }

    // 機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧（AP-06）
    // description〜ticketTypesは、SC-10からのイベント複製に必要な項目として追加
    @Transactional(readOnly = true)
    public List<DeletedEventResponse> listDeleted() {
        return eventRepository.findAllByDeletedAtIsNotNullOrderByStartAtAsc().stream()
                .map(event -> new DeletedEventResponse(
                        event.getId(),
                        event.getName(),
                        event.getStartAt(),
                        event.getPlace(),
                        event.getCapacity(),
                        event.getDeletedAt(),
                        event.getDescription(),
                        event.getOrganizerName(),
                        event.getImageUrl(),
                        event.getExtraQuestion(),
                        ticketTypeRepository.findByEvent_Id(event.getId()).stream()
                                .map(this::toTicketTypeResponse)
                                .toList()
                ))
                .toList();
    }

    // AP-05: 指定IDのイベントが無ければ404（API設計書§2）
    @Transactional(readOnly = true)
    public EventDetailResponse getDetail(Long id) {
        Event event = findByIdOrThrow(id);
        return toDetail(event);
    }

    // AP-07: イベント登録（管理者のみ。権限チェックはController側）
    @Transactional
    public EventDetailResponse create(EventUpsertRequest request) {
        Event event = new Event(
                request.name(),
                request.startAt(),
                request.place(),
                resolveCapacity(request, null),
                request.applicationDeadline(),
                request.description(),
                request.organizerName(),
                request.imageUrl(),
                request.extraQuestion()
        );
        Event saved = eventRepository.save(event);
        List<TicketType> ticketTypes = saveTicketTypes(saved, request.ticketTypes());
        log.info("イベント登録完了 userId={} eventId={} name={} capacity={}",
                authContext.getCurrentUser().userId(), saved.getId(), saved.getName(), saved.getCapacity());
        return toDetail(saved, ticketTypes);
    }

    // AP-08: イベント編集。指定IDが無ければ404
    @Transactional
    public EventDetailResponse update(Long id, EventUpsertRequest request) {
        Event event = findByIdOrThrow(id);
        event.applyChanges(
                request.name(),
                request.startAt(),
                request.place(),
                resolveCapacity(request, event),
                request.applicationDeadline(),
                request.description(),
                request.organizerName(),
                request.imageUrl(),
                request.extraQuestion()
        );
        List<TicketType> ticketTypes = saveTicketTypes(event, request.ticketTypes());
        log.info("イベント更新完了 userId={} eventId={}", authContext.getCurrentUser().userId(), event.getId());
        return toDetail(event, ticketTypes);
    }

    // 要件定義書§8: 定員は「参加区分が1件も無い場合のみ」必須。区分がある場合は区分の定員合計を用いる
    // （最終的な値はこの後saveTicketTypes()が区分の定員合計で再同期するため、ここでの計算は暫定値でよい。
    // 区分もcapacityも無ければ、ticketTypeIdの要否判定（ApplicationService.resolveTicketType）と同じ考え方でエラーとする）
    private Integer resolveCapacity(EventUpsertRequest request, Event existingEvent) {
        if (request.capacity() != null) {
            return request.capacity();
        }
        if (request.ticketTypes() != null && !request.ticketTypes().isEmpty()) {
            return request.ticketTypes().stream().mapToInt(TicketTypeRequest::capacity).sum();
        }
        // 更新時、リクエストに区分の指定が無い（＝既存の区分を維持する）場合は、既存の区分の定員合計を暫定値とする
        if (request.ticketTypes() == null && existingEvent != null) {
            List<TicketType> existing = ticketTypeRepository.findByEvent_Id(existingEvent.getId());
            if (!existing.isEmpty()) {
                return existing.stream().mapToInt(TicketType::getCapacity).sum();
            }
        }
        throw new BusinessException("定員を入力してください");
    }

    // AP-09: イベント削除。受付済の申込が1件でもあれば400、指定IDが無ければ404
    // 機能追加（ソフトデリート）: 物理削除ではなくdeleted_atを立てるのみ。「削除済みイベント」画面から復元できる。
    @Transactional
    public void delete(Long id) {
        Event event = findByIdOrThrow(id);
        if (countAccepted(event.getId()) > 0) {
            throw new BusinessException("申込があるため削除できません");
        }
        event.softDelete();
        log.info("イベント削除完了 userId={} eventId={}", authContext.getCurrentUser().userId(), event.getId());
    }

    // 機能追加（ソフトデリートの復元）。削除済みでなければ404。
    @Transactional
    public EventDetailResponse restore(Long id) {
        Event event = eventRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new NotFoundException("削除済みイベントが見つかりません"));
        event.restore();
        log.info("イベント復元完了 userId={} eventId={}", authContext.getCurrentUser().userId(), event.getId());
        return toDetail(event);
    }

    // 機能追加（定員区分）: 区分の全置換。requestsがnull＝区分の指定なし（既存の区分に手を加えない）。
    // 戻り値は更新後の区分一覧（呼び出し側がtoDetail()で再度クエリしなくて済むように）。
    // 既存の区分に受付済・キャンセル待ちの申込が残っている場合は変更を拒否する（docs/06_詳細設計書.md 7-4 AP-07／AP-08 No.6参照）。
    private List<TicketType> saveTicketTypes(Event event, List<TicketTypeRequest> requests) {
        if (requests == null) {
            // 区分を変更しない場合でも、既存の区分があるイベントはcapacityを区分の合計に保つ
            // （docs/02_テーブル定義書.md §4.2「区分がある場合はcapacityは区分の合計」との矛盾を防ぐ。
            // リクエストのcapacityは区分の無いイベントの場合のみ有効に使われる）
            List<TicketType> existing = ticketTypeRepository.findByEvent_Id(event.getId());
            if (!existing.isEmpty()) {
                event.syncCapacityFromTicketTypes(existing.stream().mapToInt(TicketType::getCapacity).sum());
            }
            return existing;
        }
        // ticketTypeが無い（区分の無いイベントだった時点で申し込まれた）申込も含めて判定する。
        // そうしないと、区分の無いイベントに初めて区分を追加した際、既存の申込がどの区分にも
        // 属さないまま区分単位の定員判定・繰り上げから漏れてしまう
        if (applicationRepository.existsByEvent_IdAndStatusIn(event.getId(), ApplicationStatus.ACTIVE_STATUSES)) {
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
        long distinctNames = requests.stream().map(TicketTypeRequest::name).distinct().count();
        if (distinctNames < requests.size()) {
            // 同一イベント内での区分名の重複を禁止する（テーブル定義書§4.3）
            throw new BusinessException("区分名が重複しています");
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
                countFavorites(event.getId())
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
                event.getExtraQuestion(),
                ticketTypes,
                countFavorites(event.getId())
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

    // （機能追加）: お気に入り登録件数。countAccepted()と同様、イベントごとに1クエリで数える
    private long countFavorites(Long eventId) {
        return favoriteRepository.countByEvent_Id(eventId);
    }
}
