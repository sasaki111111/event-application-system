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
/**
 * イベント一覧・詳細取得（AP-020・AP-021）、登録・更新・削除（AP-120〜AP-122）、削除済み一覧（AP-123）、復元（AP-124）の業務ロジックを
 * 担当するService。EventController（list／getDetail／listDeleted／create／update／delete／restore）から呼ばれ、
 * DBアクセスにはEventRepository・ApplicationRepository・TicketTypeRepository・FavoriteRepositoryを使う。
 */
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

    // AP-020: status=all(既定)は全件、status=openは申込受付中のみ（docs/30_詳細設計/31_API詳細設計書.md AP-020）
    /**
     * イベント一覧を取得する（AP-020）。EventController#listから呼ばれる。
     *
     * @param status "all"（既定、全件）または"open"（受付中のみ）
     * @return イベント一覧（開催日時昇順）
     */
    @Transactional(readOnly = true)
    public List<EventSummaryResponse> list(String status) {
        LocalDateTime now = LocalDateTime.now();
        boolean openOnly = "open".equals(status);

        // Stream API（.stream()〜.toList()）: リストの各要素を順に処理していく書き方。
        // .filter(...)は条件に合う要素だけを残し、.map(...)は各要素を別の形に変換する。
        // event -> ... の部分はラムダ式（引数eventを受け取り、その場で処理内容を定義する小さな関数）。
        return eventRepository.findAllByDeletedAtIsNullOrderByStartAtAsc().stream()
                .filter(event -> !openOnly || event.isOpen(now))
                .map(event -> toSummary(event, now))
                .toList();
    }

    // AP-123: 管理者の「削除済みイベント」一覧
    // description〜ticketTypesは、SC-122からのイベント複製に使う項目
    /**
     * 削除済み（論理削除）のイベント一覧を取得する（AP-123）。EventController#listDeletedから呼ばれる
     * （管理者権限の確認はController側で完了済み）。
     *
     * @return 削除済みイベント一覧
     */
    @Transactional(readOnly = true)
    public List<DeletedEventResponse> listDeleted() {
        // 削除済み（deleted_atに日時あり）のイベントを開催日時昇順で取得し、区分一覧を含めたレスポンスに変換する
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

    // AP-021: 指定IDのイベントが無ければ404（docs/30_詳細設計/31_API詳細設計書.md AP-021）
    /**
     * 指定したイベントの詳細を取得する（AP-021）。EventController#detailから呼ばれる。
     *
     * @param id 対象イベントID
     * @return イベント詳細
     */
    @Transactional(readOnly = true)
    public EventDetailResponse getDetail(Long id) {
        Event event = findByIdOrThrow(id);
        return toDetail(event);
    }

    // AP-120: イベント登録（管理者のみ。権限チェックはController側）
    /**
     * イベントを新規登録する（AP-120）。EventController#createから呼ばれる（管理者権限の確認はController側で完了済み）。
     *
     * @param request 登録するイベント情報・参加区分（任意）
     * @return 登録されたイベントの詳細
     */
    @Transactional
    public EventDetailResponse create(EventUpsertRequest request) {
        // リクエストの内容からイベントを組み立てる（定員はresolveCapacityで決定する。R-11）
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
        // 参加区分を保存し、区分があればcapacityを区分の定員合計に同期する
        List<TicketType> ticketTypes = saveTicketTypes(saved, request.ticketTypes());
        // 登録の実行者はeventsの行に残らないため、ログに記録する（docs/30_詳細設計/33_共通詳細設計書.md 6.1）
        log.info("イベント登録完了 eventId={} 実行者userId={}", saved.getId(), authContext.getCurrentUser().userId());
        return toDetail(saved, ticketTypes);
    }

    // AP-121: イベント編集。指定IDが無ければ404
    /**
     * イベント情報・参加区分を編集する（AP-121）。EventController#updateから呼ばれる。
     *
     * @param id      編集対象イベントID
     * @param request 編集後の内容
     * @return 編集後のイベント詳細
     */
    @Transactional
    public EventDetailResponse update(Long id, EventUpsertRequest request) {
        // 対象イベントを取得する（存在しない、または削除済みなら404・E-B-001）
        Event event = findByIdOrThrow(id);
        // 変更内容を反映する（トランザクション終了時にJPAが変更を検知してUPDATEする）
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
        // 参加区分を全置換する（指定が無ければ既存の区分を維持する）
        List<TicketType> ticketTypes = saveTicketTypes(event, request.ticketTypes());
        log.info("イベント更新完了 eventId={} 実行者userId={}", event.getId(), authContext.getCurrentUser().userId());
        return toDetail(event, ticketTypes);
    }

    // R-11・C-02: 定員は「参加区分が1件も無い場合のみ」必須。区分がある場合は区分の定員合計を用いる
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
        // 定員も区分も無ければ定員を決定できないためエラーにする（E-B-006）
        throw new BusinessException("定員を入力してください");
    }

    // AP-122: イベント削除。受付済の申込が1件でもあれば400（R-07・E-B-013）、指定IDが無ければ404
    // 物理削除ではなくdeleted_atを立てるのみ（論理削除）。「削除済みイベント」画面（SC-122）から復元できる。
    /**
     * イベントを削除（論理削除）する（AP-122）。EventController#deleteから呼ばれる。
     *
     * @param id 削除対象イベントID
     */
    @Transactional
    public void delete(Long id) {
        Event event = findByIdOrThrow(id);
        if (countAccepted(event.getId()) > 0) {
            throw new BusinessException("申込があるため削除できません");
        }
        event.softDelete();
        log.info("イベント削除完了 eventId={} 実行者userId={}", event.getId(), authContext.getCurrentUser().userId());
    }

    // AP-124: イベント復元。削除済みでなければ404（R-08・E-B-014）。
    /**
     * 削除済みのイベントを復元する（AP-124）。EventController#restoreから呼ばれる。
     *
     * @param id 復元対象イベントID
     * @return 復元後のイベント詳細
     */
    @Transactional
    public EventDetailResponse restore(Long id) {
        Event event = eventRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new NotFoundException("削除済みイベントが見つかりません"));
        event.restore();
        log.info("イベント復元完了 eventId={} 実行者userId={}", event.getId(), authContext.getCurrentUser().userId());
        return toDetail(event);
    }

    // 参加区分の全置換。requestsがnull＝区分の指定なし（既存の区分に手を加えない）。
    // 戻り値は更新後の区分一覧（呼び出し側がtoDetail()で再度クエリしなくて済むように）。
    // 既存の区分に受付済・キャンセル待ちの申込が残っている場合は変更を拒否する（docs/30_詳細設計/32_処理詳細設計書.md AP-120／AP-121 No.6参照）。
    private List<TicketType> saveTicketTypes(Event event, List<TicketTypeRequest> requests) {
        if (requests == null) {
            // 区分を変更しない場合でも、既存の区分があるイベントはcapacityを区分の合計に保つ
            // （docs/20_基本設計/22_テーブル定義書.md 4.2「参加区分を設定している間は、区分の定員合計を反映する」との矛盾を防ぐ。
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
            // R-09・E-B-007
            throw new BusinessException("区分に申込があるため変更できません");
        }
        // try/catch: DBの外部キー制約違反はDataIntegrityViolationExceptionとしてSpring Data JPAが投げる。
        // ここではその例外を捕まえ、初学者には分かりにくいDB由来の例外ではなく、業務的な意味を持つ
        // BusinessException（400エラー）に変換してControllerに伝えている。
        try {
            ticketTypeRepository.deleteByEvent_Id(event.getId());
            // 削除をこの時点でDBへ反映する（flush）。JPAは通常、登録（INSERT）を削除（DELETE）より先に
            // DBへ送るため、flushしないと、同じ区分名を登録し直すときに一意制約
            // （uk_ticket_types_event_name）に違反してしまう。編集画面は区分を変更していなくても
            // 既存の区分を毎回送るため、区分のあるイベントが保存できなくなる（Issue #15）。
            // 申込の履歴が区分を参照している場合の外部キー制約違反も、ここで発生して下のcatchで拾われる
            ticketTypeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 上のチェックは受付済・キャンセル待ちのみを見ているため、キャンセル済の申込が
            // 区分を参照したまま残っているケースはここで拾う（fk_applications_ticket_typeはRESTRICT。E-B-017）
            throw new BusinessException("区分に申込の履歴が残っているため変更できません");
        }
        // 空の配列＝区分をすべて解除した（区分の無いイベントにする）場合は、削除のみで終わる
        if (requests.isEmpty()) {
            return List.of();
        }
        long distinctNames = requests.stream().map(TicketTypeRequest::name).distinct().count();
        if (distinctNames < requests.size()) {
            // 同一イベント内での区分名の重複を禁止する（R-10・E-B-008、docs/20_基本設計/22_テーブル定義書.md 4.3）
            throw new BusinessException("区分名が重複しています");
        }
        // 新しい区分を保存し、イベントのcapacityを区分の定員合計に同期する
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
        // 学内の小規模な利用を前提とする（docs/20_基本設計/24_方式設計書.md 9章）ため、イベントごとに1クエリで数える簡潔な実装にしている
        return applicationRepository.countByEvent_IdAndStatus(eventId, ApplicationStatus.ACCEPTED);
    }

    // お気に入り登録件数（favoriteCount）。countAccepted()と同様、イベントごとに1クエリで数える
    private long countFavorites(Long eventId) {
        return favoriteRepository.countByEvent_Id(eventId);
    }
}
