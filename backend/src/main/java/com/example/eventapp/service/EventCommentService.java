package com.example.eventapp.service;

import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.EventCommentResponse;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.EventComment;
import com.example.eventapp.repository.EventCommentRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベントコメント（機能18、API-20〜22）の業務ロジック。
// 開催前後を問わず投稿可能（要件定義書§4）。削除は投稿者本人または管理者のみ（要件定義書§8 E10）。
@Service
public class EventCommentService {

    private final EventCommentRepository eventCommentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public EventCommentService(EventCommentRepository eventCommentRepository, EventRepository eventRepository,
            UserRepository userRepository) {
        this.eventCommentRepository = eventCommentRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    // API-20: イベント詳細のコメント一覧（投稿日時昇順）
    @Transactional(readOnly = true)
    public List<EventCommentResponse> list(Long eventId, Long currentUserId) {
        requireEvent(eventId);
        return eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(eventId).stream()
                .map(comment -> toResponse(comment, currentUserId))
                .toList();
    }

    // API-21: コメント投稿。イベントが存在しなければ404
    @Transactional
    public EventCommentResponse post(Long userId, Long eventId, String body) {
        Event event = requireEvent(eventId);
        EventComment saved = eventCommentRepository.save(
                new EventComment(event, userRepository.getReferenceById(userId), body));
        return toResponse(saved, userId);
    }

    // API-22: 削除可否チェック（要件定義書§8 E10）。投稿者本人または管理者のみ削除可能
    @Transactional
    public void delete(Long userId, boolean isAdmin, Long commentId) {
        EventComment comment = eventCommentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("コメントが見つかりません"));

        boolean allowed = isAdmin || comment.isOwnedBy(userId);
        if (!allowed) {
            throw new ForbiddenException("削除できません");
        }
        eventCommentRepository.delete(comment);
    }

    private Event requireEvent(Long eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));
    }

    private EventCommentResponse toResponse(EventComment comment, Long currentUserId) {
        return new EventCommentResponse(
                comment.getId(),
                comment.getUser().getName(),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.isOwnedBy(currentUserId)
        );
    }
}
