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
import org.mockito.ArgumentCaptor;

/**
 * EventCommentService（イベントへのコメント投稿・削除・一覧取得の業務ロジック）に対する単体テスト。
 * JUnit5とMockitoを使用する。Repository（DBアクセスを担うクラス）はすべてモック化（偽装）し、
 * 本物のDBに接続せずに「投稿者本人か」「返信があるので論理削除にする」等の業務ルールだけを検証する。
 */
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

    // @BeforeEachが付いたメソッドは各@Testメソッドの実行前に毎回呼ばれ、テストごとに新しいモックとServiceを用意する。
    @BeforeEach
    void setUp() {
        // mock(クラス.class)で、本物のRepository（DBに接続するクラス）の代わりに
        // 振る舞いを偽装したオブジェクトを作る。各テストメソッド内のwhen(...).thenReturn(...)で
        // 「このメソッドが呼ばれたらこの値を返す」という振る舞いを設定し、DBに依存せずServiceだけを検証する。
        eventCommentRepository = mock(EventCommentRepository.class);
        eventRepository = mock(EventRepository.class);
        userRepository = mock(UserRepository.class);
        // モック化したRepositoryを渡して、テスト対象のServiceを生成する
        eventCommentService = new EventCommentService(eventCommentRepository, eventRepository, userRepository);
    }

    // 正常系: 一覧取得時、本人の投稿にはmine=trueが付く
    @Test
    void list_正常系_本人の投稿はmineがtrue() {
        // モック化したEventを用意する（本物のEventは作らず、挙動だけ偽装する）
        Event event = mock(Event.class);
        // 「対象イベントが存在し、削除されていない」という状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 投稿者となるUserをモック化する
        User author = mock(User.class);
        // 投稿者のIDがUSER_ID（この一覧取得を呼び出す本人と同じ）であるよう設定する
        when(author.getId()).thenReturn(USER_ID);
        when(author.getName()).thenReturn("投稿者");
        // 本物のEventCommentエンティティを1件生成する（コンストラクタで投稿内容を組み立てる）
        EventComment comment = new EventComment(event, author, "コメント本文");
        // コメント一覧を取得するRepositoryメソッドが、上で作った1件を返す状況を設定する
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(EVENT_ID)).thenReturn(List.of(comment));

        // テスト対象のメソッドを実行する（USER_IDは「一覧を見ている本人」として渡す）
        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        // 結果が1件であることを確認する
        assertThat(result).hasSize(1);
        // 投稿者本人が見ているため、mine（自分の投稿か）がtrueになることを確認する
        assertThat(result.get(0).mine()).isTrue();
        // 投稿者名がレスポンスに含まれることを確認する
        assertThat(result.get(0).userName()).isEqualTo("投稿者");
    }

    // 正常系: 他人の投稿はmine=false
    @Test
    void list_正常系_他人の投稿はmineがfalse() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User author = mock(User.class);
        // 投稿者のIDを、一覧を見ている本人（USER_ID）とは別のOTHER_USER_IDに設定する
        when(author.getId()).thenReturn(OTHER_USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(EVENT_ID)).thenReturn(List.of(comment));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        // 他人の投稿なので、mineがfalseになることを確認する
        assertThat(result.get(0).mine()).isFalse();
    }

    // 異常系: 存在しないイベントのコメント一覧取得は404相当
    @Test
    void list_異常系_イベントが存在しなければNotFoundException() {
        // 対象イベントが見つからない状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        // メソッド実行時にNotFoundExceptionがスローされることを確認する
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
        // 投稿者のユーザーIDからUserの参照を取得する処理を偽装する
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        // save()が呼ばれたら、渡された引数（保存しようとしたEventComment）をそのまま返すよう設定する
        // （本物のDBのようにIDが自動採番される挙動は再現されないが、このテストでは不要）
        when(eventCommentRepository.save(any(EventComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // コメントを投稿する（parentCommentIdはnull＝返信ではない通常の投稿）
        EventCommentResponse response = eventCommentService.post(USER_ID, EVENT_ID, "こんにちは", null);

        // 投稿した本文がそのまま返ることを確認する
        assertThat(response.body()).isEqualTo("こんにちは");
        // 投稿者本人なのでmineがtrueになることを確認する
        assertThat(response.mine()).isTrue();
        // 返信ではないので、返信先IDがnullであることを確認する
        assertThat(response.parentCommentId()).isNull();
        // 投稿直後なので削除されていない（deleted=false）ことを確認する
        assertThat(response.deleted()).isFalse();
        // save()が実際に呼ばれた（保存処理が行われた）ことを確認する
        verify(eventCommentRepository).save(any(EventComment.class));
    }

    // 異常系: 存在しないイベントへの投稿は404相当
    @Test
    void post_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.post(USER_ID, EVENT_ID, "こんにちは", null))
                .isInstanceOf(NotFoundException.class);
        // 例外発生前にsave()が呼ばれていないことを確認する
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
        // 返信先となる親コメントの投稿者（本テストでは誰でもよいのでモックのまま）
        User parentAuthor = mock(User.class);
        // 返信先となる親コメントを生成する
        EventComment parentComment = new EventComment(event, parentAuthor, "元のコメント");
        // 返信先IDとイベントIDで親コメントが見つかる状況を設定する
        when(eventCommentRepository.findByIdAndEvent_Id(COMMENT_ID, EVENT_ID)).thenReturn(Optional.of(parentComment));
        when(eventCommentRepository.save(any(EventComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // COMMENT_IDを返信先として指定して投稿する
        EventCommentResponse response = eventCommentService.post(USER_ID, EVENT_ID, "返信です", COMMENT_ID);

        // 投稿した本文がそのまま返ることを確認する
        assertThat(response.body()).isEqualTo("返信です");
    }

    // 異常系: 返信先のコメントが対象イベントに存在しない場合はNotFoundException
    @Test
    void post_異常系_返信先が存在しなければNotFoundException() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 返信先のコメントが見つからない状況を設定する
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
        // 投稿者IDを、削除を実行する本人（USER_ID）と同じにする
        when(author.getId()).thenReturn(USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        // 削除対象のコメントIDで、このコメントが見つかる状況を設定する
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        // 本人（isAdmin=false）として削除を実行する
        eventCommentService.delete(USER_ID, false, COMMENT_ID);

        // 物理削除（delete）が呼ばれたことを確認する
        verify(eventCommentRepository).delete(comment);
    }

    // 正常系（要件定義書§8）: 管理者は他人のコメントも削除できる
    @Test
    void delete_正常系_管理者は他人のコメントも削除できる() {
        Event event = mock(Event.class);
        User author = mock(User.class);
        // 投稿者IDを、削除を実行する本人とは別のOTHER_USER_IDにする（他人の投稿）
        when(author.getId()).thenReturn(OTHER_USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        // isAdmin=trueとして削除を実行する（投稿者本人でなくても管理者なら削除できる）
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

        // 投稿者本人ではなく（USER_ID≠OTHER_USER_ID）、管理者でもない（isAdmin=false）状態で削除しようとすると
        // ForbiddenException（403相当）がスローされることを確認する
        assertThatThrownBy(() -> eventCommentService.delete(USER_ID, false, COMMENT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("削除できません");
        // 権限がないため、削除処理が呼ばれていないことを確認する
        verify(eventCommentRepository, never()).delete(any(EventComment.class));
    }

    // 異常系: 存在しないコメントの削除は404相当
    @Test
    void delete_異常系_コメントが存在しなければNotFoundException() {
        // 削除対象のコメントが見つからない状況を設定する
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
        // このコメントに対する返信が1件以上存在する状況を設定する
        when(eventCommentRepository.existsByParentComment_Id(COMMENT_ID)).thenReturn(true);

        eventCommentService.delete(USER_ID, false, COMMENT_ID);

        // 返信が残っているため、物理削除ではなく論理削除（deleted_atが設定された状態）になっていることを確認する
        assertThat(comment.isDeleted()).isTrue();
        // 物理削除（delete）は呼ばれていないことを確認する
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
        // このコメントを論理削除済みの状態にする
        comment.softDelete();
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(EVENT_ID)).thenReturn(List.of(comment));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        // deletedフラグがtrueになることを確認する
        assertThat(result.get(0).deleted()).isTrue();
        // 本文が元の文章ではなく、固定の案内文言に置き換わっていることを確認する
        assertThat(result.get(0).body()).isEqualTo("このコメントは削除されました");
        // 削除後も投稿者名はそのまま表示されることを確認する
        assertThat(result.get(0).userName()).isEqualTo("投稿者");
    }

    // 正常系: 一覧取得時、返信のparentCommentIdが親コメントのIDを指す
    @Test
    void list_正常系_返信のparentCommentIdが親コメントのIDになる() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User author = mock(User.class);
        when(author.getId()).thenReturn(USER_ID);
        // 返信先となる親コメントをモック化する
        EventComment parent = mock(EventComment.class);
        when(parent.getId()).thenReturn(COMMENT_ID);
        // 親コメントを返信先として指定したコメント（返信）を生成する
        EventComment reply = new EventComment(event, author, "返信です", parent);
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAscIdAsc(EVENT_ID)).thenReturn(List.of(reply));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        // レスポンスのparentCommentIdが、親コメントのIDと一致することを確認する
        assertThat(result.get(0).parentCommentId()).isEqualTo(COMMENT_ID);
    }

    // 正常系: コメント総数はリポジトリのcountByDeletedAtIsNull()をそのまま返す（論理削除済みは除外済み）
    @Test
    void countActive_正常系_リポジトリの件数を返す() {
        // 有効なコメント数（論理削除されていないもの）が7件である状況を設定する
        when(eventCommentRepository.countByDeletedAtIsNull()).thenReturn(7L);

        long result = eventCommentService.countActive();

        // Repositoryが返した件数がそのまま返ることを確認する
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
        // このユーザーの投稿履歴として、上で作ったコメントが1件返る状況を設定する
        when(eventCommentRepository.findByUser_IdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(comment));

        List<UserCommentResponse> result = eventCommentService.listByUser(USER_ID);

        // 結果が1件であることを確認する
        assertThat(result).hasSize(1);
        // 投稿先のイベントIDが含まれることを確認する
        assertThat(result.get(0).eventId()).isEqualTo(EVENT_ID);
        // 投稿先のイベント名が含まれることを確認する
        assertThat(result.get(0).eventName()).isEqualTo("テストイベント");
        // コメント本文が含まれることを確認する
        assertThat(result.get(0).body()).isEqualTo("コメント本文");
        // 削除されていないコメントなのでdeletedがfalseであることを確認する
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
        // このコメントを論理削除済みの状態にする
        comment.softDelete();
        when(eventCommentRepository.findByUser_IdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(comment));

        List<UserCommentResponse> result = eventCommentService.listByUser(USER_ID);

        // 論理削除済みでも履歴から除外されず、deletedがtrueで返ることを確認する
        assertThat(result.get(0).deleted()).isTrue();
        // 本文が固定の案内文言に置き換わっていることを確認する
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
        // 有効な（論理削除されていない）コメント一覧として、上で作った1件が返る状況を設定する
        when(eventCommentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(comment));

        List<CommentModerationResponse> result = eventCommentService.listAllActive();

        // 結果が1件であることを確認する
        assertThat(result).hasSize(1);
        // イベントID・イベント名・投稿者名・本文が、それぞれ元データと一致することを確認する
        assertThat(result.get(0).eventId()).isEqualTo(EVENT_ID);
        assertThat(result.get(0).eventName()).isEqualTo("テストイベント");
        assertThat(result.get(0).userName()).isEqualTo("投稿者");
        assertThat(result.get(0).body()).isEqualTo("コメント本文");
    }

    // 異常系（UT-CMT-10）: 返信先に別イベントのコメントを指定した場合はNotFoundException
    @Test
    void post_異常系_返信先が別イベントのコメントならNotFoundException() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // COMMENT_IDのコメントは別のイベント（ID=99）に属しており、投稿先イベント（EVENT_ID）では見つからない状況を設定する
        EventComment commentOfOtherEvent = new EventComment(mock(Event.class), mock(User.class), "別イベントのコメント");
        when(eventCommentRepository.findByIdAndEvent_Id(COMMENT_ID, 99L)).thenReturn(Optional.of(commentOfOtherEvent));
        when(eventCommentRepository.findByIdAndEvent_Id(COMMENT_ID, EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.post(USER_ID, EVENT_ID, "返信です", COMMENT_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("返信先のコメントが見つかりません");
        // 返信先は「コメントID＋投稿先イベントID」の組で検索されることを確認する
        verify(eventCommentRepository).findByIdAndEvent_Id(COMMENT_ID, EVENT_ID);
        verify(eventCommentRepository, never()).save(any());
    }

    // 正常系（UT-CMT-11）: 論理削除済みのコメントにも返信できる
    @Test
    void post_正常系_論理削除済みのコメントにも返信できる() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        // 返信先となる親コメントを、論理削除済みの状態にしておく
        EventComment parentComment = new EventComment(event, mock(User.class), "元のコメント");
        parentComment.softDelete();
        when(eventCommentRepository.findByIdAndEvent_Id(COMMENT_ID, EVENT_ID)).thenReturn(Optional.of(parentComment));
        ArgumentCaptor<EventComment> captor = ArgumentCaptor.forClass(EventComment.class);
        when(eventCommentRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        EventCommentResponse response = eventCommentService.post(USER_ID, EVENT_ID, "返信です", COMMENT_ID);

        // エラーにならず、論理削除済みの親コメントに紐づく返信として保存されることを確認する
        assertThat(response.body()).isEqualTo("返信です");
        assertThat(captor.getValue().getParentComment()).isSameAs(parentComment);
        assertThat(captor.getValue().getParentComment().isDeleted()).isTrue();
    }
}
