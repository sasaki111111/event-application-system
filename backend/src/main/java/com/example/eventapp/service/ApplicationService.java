package com.example.eventapp.service;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベント申込（D-3）の業務ロジック。
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public ApplicationService(ApplicationRepository applicationRepository, EventRepository eventRepository,
            UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    // API-03: 定員超過・締切/日付・二重申込のチェック（要件定義書§8）。userIdは呼出元（Controller）がX-User-Idから渡す
    @Transactional
    public ApplicationResponse apply(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        if (!event.isOpen(LocalDateTime.now())) {
            throw new BusinessException("申込受付は終了しました");
        }

        long acceptedCount = applicationRepository.countByEvent_IdAndStatus(eventId, ApplicationStatus.ACCEPTED);
        if (acceptedCount >= event.getCapacity()) {
            throw new BusinessException("定員に達しています");
        }

        boolean alreadyApplied = applicationRepository
                .existsByUser_IdAndEvent_IdAndStatus(userId, eventId, ApplicationStatus.ACCEPTED);
        if (alreadyApplied) {
            throw new BusinessException("すでに申し込み済みです");
        }

        // 存在確認済みのIDなのでDBに問い合わせない参照を使う（AuthInterceptorがuserIdを、上のfindByIdがeventを検証済み）
        Application application = new Application(userRepository.getReferenceById(userId), event);
        Application saved = applicationRepository.save(application);

        return new ApplicationResponse(
                saved.getId(),
                event.getId(),
                userId,
                saved.getStatus(),
                saved.getAppliedAt()
        );
    }
}
