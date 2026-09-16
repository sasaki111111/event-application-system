package com.example.eventapp.service;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベント申込（D-3）、自分の申込一覧（D-4）、申込キャンセル（D-5）の業務ロジック。
// 機能追加：定員超過時は400で拒否せず「キャンセル待ち」として登録し、受付済がキャンセルされた際に
// 最も古いキャンセル待ちを自動で「受付済」に繰り上げる。
@Service
public class ApplicationService {

    private static final Set<String> ACTIVE_STATUSES = Set.of(ApplicationStatus.ACCEPTED, ApplicationStatus.WAITLISTED);

    private final ApplicationRepository applicationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public ApplicationService(ApplicationRepository applicationRepository, EventRepository eventRepository,
            UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    // API-03: 締切/日付・二重申込のチェック（要件定義書§8）。定員に達している場合はキャンセル待ちとして登録する。
    // userIdは呼出元（Controller）がX-User-Idから渡す
    @Transactional
    public ApplicationResponse apply(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        if (!event.isOpen(LocalDateTime.now())) {
            throw new BusinessException("申込受付は終了しました");
        }

        boolean alreadyApplied = applicationRepository
                .existsByUser_IdAndEvent_IdAndStatusIn(userId, eventId, ACTIVE_STATUSES);
        if (alreadyApplied) {
            throw new BusinessException("すでに申し込み済みです");
        }

        long acceptedCount = applicationRepository.countByEvent_IdAndStatus(eventId, ApplicationStatus.ACCEPTED);
        String status = acceptedCount < event.getCapacity() ? ApplicationStatus.ACCEPTED : ApplicationStatus.WAITLISTED;

        // 存在確認済みのIDなのでDBに問い合わせない参照を使う（AuthInterceptorがuserIdを、上のfindByIdがeventを検証済み）
        Application application = new Application(userRepository.getReferenceById(userId), event, status);
        Application saved = applicationRepository.save(application);

        return new ApplicationResponse(
                saved.getId(),
                event.getId(),
                userId,
                saved.getStatus(),
                saved.getAppliedAt()
        );
    }

    // API-04: 自分の申込一覧（申込日時降順）。本人分のみ返す＝userIdでの絞り込みそのもの
    @Transactional(readOnly = true)
    public List<MyApplicationResponse> myApplications(Long userId) {
        return applicationRepository.findByUser_IdOrderByAppliedAtDesc(userId).stream()
                .map(this::toMyApplicationResponse)
                .toList();
    }

    // API-05: 取消可否チェック（要件定義書§8）。本人の申込以外は403。
    // 受付済をキャンセルした場合、最も古いキャンセル待ちがあれば自動で繰り上げる（機能追加）。
    @Transactional
    public void cancel(Long userId, Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("申込が見つかりません"));

        if (!application.getUser().getId().equals(userId)) {
            throw new ForbiddenException("権限がありません");
        }

        boolean cancellable = ACTIVE_STATUSES.contains(application.getStatus())
                && LocalDateTime.now().isBefore(application.getEvent().getStartAt());
        if (!cancellable) {
            throw new BusinessException("取消できません");
        }

        boolean wasAccepted = ApplicationStatus.ACCEPTED.equals(application.getStatus());
        Long eventId = application.getEvent().getId();
        application.cancel();

        if (wasAccepted) {
            applicationRepository
                    .findFirstByEvent_IdAndStatusOrderByAppliedAtAsc(eventId, ApplicationStatus.WAITLISTED)
                    .ifPresent(Application::promote);
        }
    }

    private MyApplicationResponse toMyApplicationResponse(Application application) {
        Event event = application.getEvent();
        return new MyApplicationResponse(
                application.getId(),
                event.getId(),
                event.getName(),
                event.getStartAt(),
                application.getStatus(),
                application.getAppliedAt()
        );
    }
}
