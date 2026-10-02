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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベントコメント（AP-19〜21）の業務ロジック。
// 開催前後を問わず投稿可能。削除は投稿者本人または管理者のみ。
/**
 * イベントコメントの一覧（AP-19）・投稿（AP-20）・削除（AP-21）、管理者向けの総数取得（AP-30）・
 * 全件一覧（AP-32）・利用者別履歴（AP-31）の業務ロジックを担当するService。
 * CommentController（list／post／delete／count／listAll）とUserController#commentsOf（AP-31）から呼ばれ、
 * DBアクセスにはEventCommentRepository・EventRepository・UserRepositoryを使う。
 */
@Service
public class EventCommentService {

    private static final Logger log = LoggerFactory.getLogger(EventCommentService.class);

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
    /**
     * 対象イベントのコメント一覧を取得する（AP-19）。CommentController#listから呼ばれる。
     *
     * @param eventId       対象イベントID
     * @param currentUserId 要求元の利用者ID（各コメントが本人の投稿かどうかの判定に使う）
     * @return コメント一覧（投稿日時昇順、フラットな配列）
     */
    @Transactional(readOnly = true)
    public List<EventCommentResponse> list(Long eventId, Long currentUserId) {
        requireEvent(eventId);
        // comment -> toResponse(comment, currentUserId): ラムダ式。引数commentを受け取り、
        // toResponse()を呼んだ結果を返す小さな関数をその場で定義している（中括弧やreturnを省略した書き方）。
        return eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(eventId).stream()
                .map(comment -> toResponse(comment, currentUserId))
                .toList();
    }

    // AP-20: コメント投稿。イベントが存在しなければ404（parentCommentIdを指定すると返信になる）
    /**
     * イベントにコメントを投稿する（AP-20）。CommentController#postから呼ばれる。
     *
     * @param userId         投稿者の利用者ID
     * @param eventId        投稿対象のイベントID
     * @param body           コメント本文
     * @param parentCommentId 返信先コメントID（返信でなければNULL）
     * @return 登録されたコメントの内容
     */
    @Transactional
    public EventCommentResponse post(Long userId, Long eventId, String body, Long parentCommentId) {
        Event event = requireEvent(eventId);
        EventComment parentComment = null;
        if (parentCommentId != null) {
            // 返信先は同一イベントのコメントであることを要する。削除済み（論理削除）のコメントへの返信は許可する
            parentComment = eventCommentRepository.findByIdAndEvent_Id(parentCommentId, eventId)
                    .orElseThrow(() -> new NotFoundException("返信先のコメントが見つかりません"));
        }
        EventComment saved = eventCommentRepository.save(
                new EventComment(event, userRepository.getReferenceById(userId), body, parentComment));
        log.info("コメント投稿完了 userId={} commentId={} eventId={} parentCommentId={}",
                userId, saved.getId(), eventId, parentCommentId);
        return toResponse(saved, userId);
    }

    // AP-21: 削除可否チェック。投稿者本人または管理者のみ削除可能。
    // 返信が1件も無ければ物理削除、1件以上あれば返信との参照関係を保つため論理削除する
    /**
     * コメントを削除する（AP-21）。CommentController#deleteから呼ばれる。投稿者本人・管理者のいずれでもない場合は
     * ForbiddenExceptionを投げ、GlobalExceptionHandlerにより403（Forbidden）になる。
     *
     * @param userId    削除を実行する利用者ID
     * @param isAdmin   実行者が管理者かどうか
     * @param commentId 削除対象のコメントID
     */
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
            log.info("コメント論理削除完了 userId={} commentId={}", userId, commentId);
        } else {
            eventCommentRepository.delete(comment);
            log.info("コメント物理削除完了 userId={} commentId={}", userId, commentId);
        }
    }

    // 管理者ダッシュボードのコメント総数（論理削除済みは除外、AP-30）
    /**
     * 管理者ダッシュボード（SC-07）向けの、有効なコメント総数を取得する（AP-30）。CommentController#countから呼ばれる。
     *
     * @return コメント総数（論理削除されたものは除く）
     */
    @Transactional(readOnly = true)
    public long countActive() {
        return eventCommentRepository.countByDeletedAtIsNull();
    }

    // 利用者詳細（SC-15）のコメント履歴（AP-31）。論理削除済みのコメントも含める
    /**
     * 指定した利用者の投稿履歴を取得する（AP-31）。UserController#commentsOfから呼ばれる。
     *
     * @param userId 対象の利用者ID
     * @return コメント履歴（投稿日時降順、論理削除済みのコメントも含む）
     */
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

    // 全コメント一覧（SC-16コメントモデレーション、AP-32）。有効なコメントのみを対象とする
    /**
     * SC-16（コメントモデレーション画面）向けに、全イベント横断の有効なコメント一覧を取得する（AP-32）。
     * CommentController#listAllから呼ばれる。
     *
     * @return コメント一覧（投稿日時降順、論理削除されたものは除く）
     */
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
