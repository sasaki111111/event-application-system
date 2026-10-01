package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.CommentModerationResponse;
import com.example.eventapp.dto.EventCommentResponse;
import com.example.eventapp.dto.UserCommentResponse;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.EventComment;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.EventCommentRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 実行環境: サーバー側（JVM）。EventCommentServiceの業務ロジック（要件定義書§8 E10）のユニットテスト。
// JUnit5＋Mockito。Repositoryは全てモック化し、DBに触れずにビジネスロジックだけを検証する。
class EventCommentServiceTest {

    private EventCommentRepository eventCommentRepository;
    private EventRepository eventRepository;
    private UserRepository userRepository;
    private EventCommentService eventCommentService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long EVENT_ID = 10L;
    private static final Long COMMENT_ID = 100L;

    @BeforeEach
    void setUp() {
        eventCommentRepository = mock(EventCommentRepository.class);
        eventRepository = mock(EventRepository.class);
        userRepository = mock(UserRepository.class);
        eventCommentService = new EventCommentService(eventCommentRepository, eventRepository, userRepository);
    }

    // 正常系: 一覧取得時、本人の投稿にはmine=trueが付く
    @Test
    void list_正常系_本人の投稿はmineがtrue() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User author = mock(User.class);
        when(author.getId()).thenReturn(USER_ID);
        when(author.getName()).thenReturn("投稿者");
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(EVENT_ID)).thenReturn(List.of(comment));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mine()).isTrue();
        assertThat(result.get(0).userName()).isEqualTo("投稿者");
    }

    // 正常系: 他人の投稿はmine=false
    @Test
    void list_正常系_他人の投稿はmineがfalse() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User author = mock(User.class);
        when(author.getId()).thenReturn(OTHER_USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(EVENT_ID)).thenReturn(List.of(comment));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        assertThat(result.get(0).mine()).isFalse();
    }

    // 異常系: 存在しないイベントのコメント一覧取得は404相当
    @Test
    void list_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.list(EVENT_ID, USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // 正常系: コメント投稿が保存され、投稿者本人としてmine=trueで返る
    @Test
    void post_正常系_保存されmineがtrueで返る() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getName()).thenReturn("投稿者");
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(eventCommentRepository.save(any(EventComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventCommentResponse response = eventCommentService.post(USER_ID, EVENT_ID, "こんにちは", null);

        assertThat(response.body()).isEqualTo("こんにちは");
        assertThat(response.mine()).isTrue();
        assertThat(response.parentCommentId()).isNull();
        assertThat(response.deleted()).isFalse();
        verify(eventCommentRepository).save(any(EventComment.class));
    }

    // 異常系: 存在しないイベントへの投稿は404相当
    @Test
    void post_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.post(USER_ID, EVENT_ID, "こんにちは", null))
                .isInstanceOf(NotFoundException.class);
        verify(eventCommentRepository, never()).save(any());
    }

    // 正常系: parentCommentIdを指定すると返信として保存され、レスポンスのparentCommentIdに反映される
    @Test
    void post_正常系_返信先を指定すると返信として保存される() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        User parentAuthor = mock(User.class);
        EventComment parentComment = new EventComment(event, parentAuthor, "元のコメント");
        when(eventCommentRepository.findByIdAndEvent_Id(COMMENT_ID, EVENT_ID)).thenReturn(Optional.of(parentComment));
        when(eventCommentRepository.save(any(EventComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventCommentResponse response = eventCommentService.post(USER_ID, EVENT_ID, "返信です", COMMENT_ID);

        assertThat(response.body()).isEqualTo("返信です");
    }

    // 異常系: 返信先のコメントが対象イベントに存在しない場合はNotFoundException
    @Test
    void post_異常系_返信先が存在しなければNotFoundException() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventCommentRepository.findByIdAndEvent_Id(COMMENT_ID, EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.post(USER_ID, EVENT_ID, "返信です", COMMENT_ID))
                .isInstanceOf(NotFoundException.class);
        verify(eventCommentRepository, never()).save(any());
    }

    // 正常系: 投稿者本人は自分のコメントを削除できる
    @Test
    void delete_正常系_投稿者本人は削除できる() {
        Event event = mock(Event.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        eventCommentService.delete(USER_ID, false, COMMENT_ID);

        verify(eventCommentRepository).delete(comment);
    }

    // 正常系（要件定義書§8）: 管理者は他人のコメントも削除できる
    @Test
    void delete_正常系_管理者は他人のコメントも削除できる() {
        Event event = mock(Event.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(OTHER_USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        eventCommentService.delete(USER_ID, true, COMMENT_ID);

        verify(eventCommentRepository).delete(comment);
    }

    // 異常系（要件定義書§8 E10）: 投稿者本人でも管理者でもない一般ユーザーは削除できない
    @Test
    void delete_異常系_本人でも管理者でもなければ削除できない() {
        Event event = mock(Event.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(OTHER_USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> eventCommentService.delete(USER_ID, false, COMMENT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("削除できません");
        verify(eventCommentRepository, never()).delete(any(EventComment.class));
    }

    // 異常系: 存在しないコメントの削除は404相当
    @Test
    void delete_異常系_コメントが存在しなければNotFoundException() {
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.delete(USER_ID, false, COMMENT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // 正常系: 返信が1件以上あるコメントの削除は、物理削除せず論理削除（deleted_atの設定）にとどめる
    @Test
    void delete_正常系_返信があるコメントは物理削除せず論理削除する() {
        Event event = mock(Event.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(eventCommentRepository.existsByParentComment_Id(COMMENT_ID)).thenReturn(true);

        eventCommentService.delete(USER_ID, false, COMMENT_ID);

        assertThat(comment.isDeleted()).isTrue();
        verify(eventCommentRepository, never()).delete(any(EventComment.class));
    }

    // 正常系: 一覧取得時、論理削除されたコメントは本文が固定文言に置き換わり、deletedがtrueになる
    @Test
    void list_正常系_論理削除されたコメントは本文が固定文言になる() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User author = mock(User.class);
        when(author.getId()).thenReturn(USER_ID);
        when(author.getName()).thenReturn("投稿者");
        EventComment comment = new EventComment(event, author, "元のコメント");
        comment.softDelete();
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(EVENT_ID)).thenReturn(List.of(comment));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        assertThat(result.get(0).deleted()).isTrue();
        assertThat(result.get(0).body()).isEqualTo("このコメントは削除されました");
        assertThat(result.get(0).userName()).isEqualTo("投稿者");
    }

    // 正常系: 一覧取得時、返信のparentCommentIdが親コメントのIDを指す
    @Test
    void list_正常系_返信のparentCommentIdが親コメントのIDになる() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User author = mock(User.class);
        when(author.getId()).thenReturn(USER_ID);
        EventComment parent = mock(EventComment.class);
        when(parent.getId()).thenReturn(COMMENT_ID);
        EventComment reply = new EventComment(event, author, "返信です", parent);
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(EVENT_ID)).thenReturn(List.of(reply));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        assertThat(result.get(0).parentCommentId()).isEqualTo(COMMENT_ID);
    }

    // 正常系: コメント総数はリポジトリのcountByDeletedAtIsNull()をそのまま返す（論理削除済みは除外済み）
    @Test
    void countActive_正常系_リポジトリの件数を返す() {
        when(eventCommentRepository.countByDeletedAtIsNull()).thenReturn(7L);

        long result = eventCommentService.countActive();

        assertThat(result).isEqualTo(7L);
    }

    // 正常系: 利用者のコメント履歴に、投稿先のイベント名が含まれる
    @Test
    void listByUser_正常系_イベント名を含めて返す() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("テストイベント");
        User author = mock(User.class);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findByUser_IdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(comment));

        List<UserCommentResponse> result = eventCommentService.listByUser(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventId()).isEqualTo(EVENT_ID);
        assertThat(result.get(0).eventName()).isEqualTo("テストイベント");
        assertThat(result.get(0).body()).isEqualTo("コメント本文");
        assertThat(result.get(0).deleted()).isFalse();
    }

    // 正常系: 論理削除済みのコメントも履歴に含め、本文は固定文言・deletedはtrueになる
    @Test
    void listByUser_正常系_論理削除済みも本文が固定文言で含まれる() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("テストイベント");
        User author = mock(User.class);
        EventComment comment = new EventComment(event, author, "元のコメント");
        comment.softDelete();
        when(eventCommentRepository.findByUser_IdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(comment));

        List<UserCommentResponse> result = eventCommentService.listByUser(USER_ID);

        assertThat(result.get(0).deleted()).isTrue();
        assertThat(result.get(0).body()).isEqualTo("このコメントは削除されました");
    }

    // 正常系: 全コメント一覧（モデレーション用）に、イベント名・投稿者名が含まれる
    @Test
    void listAllActive_正常系_イベント名と投稿者名を含めて返す() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("テストイベント");
        User author = mock(User.class);
        when(author.getName()).thenReturn("投稿者");
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(comment));

        List<CommentModerationResponse> result = eventCommentService.listAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventId()).isEqualTo(EVENT_ID);
        assertThat(result.get(0).eventName()).isEqualTo("テストイベント");
        assertThat(result.get(0).userName()).isEqualTo("投稿者");
        assertThat(result.get(0).body()).isEqualTo("コメント本文");
    }
}
