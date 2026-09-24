package com.example.eventapp.service;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.AttendeeResponse;
import com.example.eventapp.dto.CheckInResponse;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.TicketType;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.TicketTypeRepository;
import com.example.eventapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベント申込（D-3）、自分の申込一覧（D-4）、申込キャンセル（D-5）の業務ロジック。
// 機能追加：定員超過時は400で拒否せず「キャンセル待ち」として登録し、受付済がキャンセルされた際に
// 最も古いキャンセル待ちを自動で「受付済」に繰り上げる。
// 機能追加（定員区分）: 区分があるイベントは区分単位で定員判定・繰り上げ・順位計算を行う（詳細設計書_v2.0.md§3.3.3〜3.3.5）。
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TicketTypeRepository ticketTypeRepository;

    public ApplicationService(ApplicationRepository applicationRepository, EventRepository eventRepository,
            UserRepository userRepository, TicketTypeRepository ticketTypeRepository) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.ticketTypeRepository = ticketTypeRepository;
    }

    // API-03: 締切/日付・二重申込・区分存在/必須のチェック（要件定義書§8）。
    // 定員（区分がある場合は区分単位、無い場合はイベント単位）に達している場合はキャンセル待ちとして登録する。
    // userIdは呼出元（Controller）がX-User-Idから渡す
    @Transactional
    public ApplicationResponse apply(Long userId, Long eventId, Long ticketTypeId, String extraAnswer) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        if (!event.isOpen(LocalDateTime.now())) {
            throw new BusinessException("申込受付は終了しました");
        }

        boolean alreadyApplied = applicationRepository
                .existsByUser_IdAndEvent_IdAndStatusIn(userId, eventId, ApplicationStatus.ACTIVE_STATUSES);
        if (alreadyApplied) {
            throw new BusinessException("すでに申し込み済みです");
        }

        TicketType ticketType = resolveTicketType(eventId, ticketTypeId);
        int capacity = ticketType != null ? ticketType.getCapacity() : event.getCapacity();
        long acceptedCount = ticketType != null
                ? applicationRepository.countByTicketType_IdAndStatus(ticketType.getId(), ApplicationStatus.ACCEPTED)
                : applicationRepository.countByEvent_IdAndStatus(eventId, ApplicationStatus.ACCEPTED);
        String status = acceptedCount < capacity ? ApplicationStatus.ACCEPTED : ApplicationStatus.WAITLISTED;

        // 存在確認済みのIDなのでDBに問い合わせない参照を使う（AuthInterceptorがuserIdを、上のfindByIdがeventを検証済み）
        Application application = new Application(
                userRepository.getReferenceById(userId), event, ticketType, status, extraAnswer);
        Application saved = applicationRepository.save(application);

        return new ApplicationResponse(
                saved.getId(),
                event.getId(),
                ticketType != null ? ticketType.getId() : null,
                userId,
                saved.getStatus(),
                saved.getAppliedAt()
        );
    }

    // 区分存在・必須チェック（要件定義書§8 E11・E12）。区分の無いイベントはticketTypeIdを無視する（API設計書§0）。
    private TicketType resolveTicketType(Long eventId, Long ticketTypeId) {
        if (!ticketTypeRepository.existsByEvent_Id(eventId)) {
            return null;
        }
        if (ticketTypeId == null) {
            throw new BusinessException("区分を選択してください");
        }
        return ticketTypeRepository.findByIdAndEvent_Id(ticketTypeId, eventId)
                .orElseThrow(() -> new NotFoundException("指定された区分が見つかりません"));
    }

    // API-04: 自分の申込一覧（申込日時降順）。本人分のみ返す＝userIdでの絞り込みそのもの
    @Transactional(readOnly = true)
    public List<MyApplicationResponse> myApplications(Long userId) {
        return applicationRepository.findByUser_IdOrderByAppliedAtDesc(userId).stream()
                .map(this::toMyApplicationResponse)
                .toList();
    }

    // API-05: 取消可否チェック（要件定義書§8）。本人の申込以外は403。
    // 受付済をキャンセルした場合、同一イベント（区分がある場合は同一区分）で最も古いキャンセル待ちを自動で繰り上げる。
    @Transactional
    public void cancel(Long userId, Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("申込が見つかりません"));

        if (!application.getUser().getId().equals(userId)) {
            throw new ForbiddenException("権限がありません");
        }

        boolean cancellable = ApplicationStatus.ACTIVE_STATUSES.contains(application.getStatus())
                && LocalDateTime.now().isBefore(application.getEvent().getStartAt());
        if (!cancellable) {
            throw new BusinessException("取消できません");
        }

        boolean wasAccepted = ApplicationStatus.ACCEPTED.equals(application.getStatus());
        Long eventId = application.getEvent().getId();
        TicketType ticketType = application.getTicketType();
        application.cancel();

        if (wasAccepted) {
            Optional<Application> nextInLine = ticketType != null
                    ? applicationRepository.findFirstByTicketType_IdAndStatusOrderByAppliedAtAsc(
                            ticketType.getId(), ApplicationStatus.WAITLISTED)
                    : applicationRepository.findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(
                            eventId, ApplicationStatus.WAITLISTED);
            nextInLine.ifPresent(Application::promote);
        }
    }

    // API-18: 当日受付の申込者一覧（申込日時昇順、機能追加）。管理者権限はController側で確認済み
    @Transactional(readOnly = true)
    public List<AttendeeResponse> listAttendees(Long eventId) {
        eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        return applicationRepository.findByEvent_IdOrderByAppliedAtAsc(eventId).stream()
                .map(this::toAttendeeResponse)
                .toList();
    }

    // API-19: チェックイン可否チェック（要件定義書§8 E8、機能追加）。「受付済」以外は拒否
    @Transactional
    public CheckInResponse checkIn(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("申込が見つかりません"));

        if (!ApplicationStatus.ACCEPTED.equals(application.getStatus())) {
            throw new BusinessException("受付済の申込のみチェックインできます");
        }
        application.checkIn();

        return new CheckInResponse(application.getId(), application.getCheckedInAt());
    }

    private AttendeeResponse toAttendeeResponse(Application application) {
        return new AttendeeResponse(
                application.getId(),
                application.getUser().getName(),
                application.getTicketType() != null ? application.getTicketType().getName() : null,
                application.getStatus(),
                application.getCheckedInAt()
        );
    }

    private MyApplicationResponse toMyApplicationResponse(Application application) {
        Event event = application.getEvent();
        return new MyApplicationResponse(
                application.getId(),
                event.getId(),
                event.getName(),
                event.getStartAt(),
                application.getStatus(),
                application.getAppliedAt(),
                calculateWaitlistRank(application)
        );
    }

    // キャンセル待ちの順位計算（要件定義書§8）: 自分より申込日時が古い、同一イベント
    // （区分がある場合は同一区分）の「キャンセル待ち」件数＋1。キャンセル待ち以外はNULL
    private Long calculateWaitlistRank(Application application) {
        if (!ApplicationStatus.WAITLISTED.equals(application.getStatus())) {
            return null;
        }
        TicketType ticketType = application.getTicketType();
        long earlierCount = ticketType != null
                ? applicationRepository.countByTicketType_IdAndStatusAndAppliedAtLessThan(
                        ticketType.getId(), ApplicationStatus.WAITLISTED, application.getAppliedAt())
                : applicationRepository.countByEvent_IdAndTicketTypeIsNullAndStatusAndAppliedAtLessThan(
                        application.getEvent().getId(), ApplicationStatus.WAITLISTED, application.getAppliedAt());
        return earlierCount + 1;
    }
}
