package com.example.eventapp.service;

import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.CommentModerationResponse;
import com.example.eventapp.dto.EventCommentResponse;
import com.example.eventapp.dto.UserCommentResponse;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.EventComment;
import com.example.eventapp.repository.EventCommentRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベントコメント（機能18、AP-19〜21）の業務ロジック。
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

    // AP-19: イベント詳細のコメント一覧（投稿日時昇順）
    @Transactional(readOnly = true)
    public List<EventCommentResponse> list(Long eventId, Long currentUserId) {
        requireEvent(eventId);
        return eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(eventId).stream()
                .map(comment -> toResponse(comment, currentUserId))
                .toList();
    }

    // AP-20: コメント投稿。イベントが存在しなければ404（D-18: parentCommentIdを指定すると返信になる）
    @Transactional
    public EventCommentResponse post(Long userId, Long eventId, String body, Long parentCommentId) {
        Event event = requireEvent(eventId);
        EventComment parentComment = null;
        if (parentCommentId != null) {
            // 返信先は同一イベントのコメントであることを要する。削除済み（論理削除）のコメントへの返信は許可する（D-18）
            parentComment = eventCommentRepository.findByIdAndEvent_Id(parentCommentId, eventId)
                    .orElseThrow(() -> new NotFoundException("返信先のコメントが見つかりません"));
        }
        EventComment saved = eventCommentRepository.save(
                new EventComment(event, userRepository.getReferenceById(userId), body, parentComment));
        return toResponse(saved, userId);
    }

    // AP-21: 削除可否チェック（要件定義書§8 E10）。投稿者本人または管理者のみ削除可能。
    // D-18: 返信が1件も無ければ物理削除、1件以上あれば返信との参照関係を保つため論理削除する
    @Transactional
    public void delete(Long userId, boolean isAdmin, Long commentId) {
        EventComment comment = eventCommentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("コメントが見つかりません"));

        boolean allowed = isAdmin || comment.isOwnedBy(userId);
        if (!allowed) {
            throw new ForbiddenException("削除できません");
        }

        if (eventCommentRepository.existsByParentComment_Id(commentId)) {
            comment.softDelete();
        } else {
            eventCommentRepository.delete(comment);
        }
    }

    // D-20: 管理者ダッシュボードのコメント総数（論理削除済みは除外、AP-30）
    @Transactional(readOnly = true)
    public long countActive() {
        return eventCommentRepository.countByDeletedAtIsNull();
    }

    // D-21: 利用者詳細（SC-15）のコメント履歴（AP-31）。論理削除済みのコメントも含める
    @Transactional(readOnly = true)
    public List<UserCommentResponse> listByUser(Long userId) {
        return eventCommentRepository.findByUser_IdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(comment -> new UserCommentResponse(
                        comment.getId(),
                        comment.getEvent().getId(),
                        comment.getEvent().getName(),
                        comment.isDeleted() ? DELETED_BODY_PLACEHOLDER : comment.getBody(),
                        comment.getCreatedAt(),
                        comment.isDeleted()
                ))
                .toList();
    }

    // D-22: 全コメント一覧（SC-16コメントモデレーション、AP-32）。有効なコメントのみを対象とする
    @Transactional(readOnly = true)
    public List<CommentModerationResponse> listAllActive() {
        return eventCommentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(comment -> new CommentModerationResponse(
                        comment.getId(),
                        comment.getEvent().getId(),
                        comment.getEvent().getName(),
                        comment.getUser().getName(),
                        comment.getBody(),
                        comment.getCreatedAt()
                ))
                .toList();
    }

    private Event requireEvent(Long eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));
    }

    private static final String DELETED_BODY_PLACEHOLDER = "このコメントは削除されました";

    private EventCommentResponse toResponse(EventComment comment, Long currentUserId) {
        Long parentCommentId = comment.getParentComment() != null ? comment.getParentComment().getId() : null;
        String body = comment.isDeleted() ? DELETED_BODY_PLACEHOLDER : comment.getBody();
        return new EventCommentResponse(
                comment.getId(),
                comment.getUser().getName(),
                body,
                comment.getCreatedAt(),
                comment.isOwnedBy(currentUserId),
                parentCommentId,
                comment.isDeleted()
        );
    }
}
