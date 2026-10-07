package com.example.eventapp.service;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CodeNameResolver;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。イベント申込、自分の申込一覧、申込キャンセル、当日受付の業務ロジック。
// 機能追加：定員超過時は400で拒否せず「キャンセル待ち」として登録し、受付済がキャンセルされた際に
// 最も古いキャンセル待ちを自動で「受付済」に繰り上げる。
// 機能追加（定員区分）: 区分があるイベントは区分単位で定員判定・繰り上げ・順位計算を行う（docs/30_詳細設計/32_処理詳細設計書.md）。
/**
 * 【Serviceとは】Serviceは、Controller（HTTPリクエストの入口）とRepository（DBアクセス）の間に立って、
 * アプリケーションの業務ロジック（「定員に空きがあるか」「本人の申込か」等の判断・処理）をまとめる層。
 * ControllerはServiceのpublicメソッドを呼ぶだけで、DBの操作やビジネスルールの詳細を知らなくて済む。
 * {@code @Service}は、このクラスがSpring Bootにより自動生成・管理される「Service層のコンポーネント」であることを示す
 * アノテーション（ControllerでのDI＝依存性の注入の仕組みは、Serviceにも同様に当てはまる）。
 * <p>
 * このクラスは、イベント申込・自分の申込一覧・申込キャンセル・当日受付チェックインの業務ロジックを担当し、
 * ApplicationController（apply／myApplications／cancel）とEventController（attendees）、
 * UserController（他利用者の申込一覧、AP-142）から呼ばれる。DBアクセスにはApplicationRepository・
 * EventRepository・UserRepository・TicketTypeRepositoryを使う。
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applicationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final AuthContext authContext;
    private final CodeNameResolver codeNameResolver;

    public ApplicationService(ApplicationRepository applicationRepository, EventRepository eventRepository,
            UserRepository userRepository, TicketTypeRepository ticketTypeRepository, AuthContext authContext,
            CodeNameResolver codeNameResolver) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.authContext = authContext;
        this.codeNameResolver = codeNameResolver;
    }

    // AP-030: 締切/日付・二重申込・区分存在/必須のチェック（要件定義書§8）。
    // 定員（区分がある場合は区分単位、無い場合はイベント単位）に達している場合はキャンセル待ちとして登録する。
    // userIdは呼出元（Controller）がX-User-Idから渡す
    /**
     * イベントへの参加申込を登録する（AP-030）。ApplicationController#applyから呼ばれ、ApplicationRepository・
     * EventRepository・UserRepository・TicketTypeRepositoryを使って登録・判定を行う。
     * {@code @Transactional}は、このメソッド内のDB操作をひとつのトランザクション（一連の処理をまとめて
     * 成功／失敗させる単位）として実行することを表す。途中で例外が発生すると、ここまでの変更はすべて
     * 取り消され（ロールバック）、DBには反映されない。
     *
     * @param userId      申込者の利用者ID（Controllerが認証情報から渡す）
     * @param eventId     申込対象のイベントID
     * @param ticketTypeId 参加区分ID（区分の無いイベントではNULL）
     * @param extraAnswer アンケート回答（アンケートが無いイベントではNULL）
     * @return 登録された申込の内容（受付済／キャンセル待ちのいずれか）
     */
    @Transactional
    public ApplicationResponse apply(Long userId, Long eventId, Long ticketTypeId, String extraAnswer) {
        // Optional<Event>.orElseThrow(...): findByIdAndDeletedAtIsNullはOptional（値があるかどうかを
        // 表す箱）を返す。値が入っていればそれを取り出し、空（該当イベントが無い）ならラムダ式
        // （() -> ...の部分、その場で定義する小さな関数）でNotFoundExceptionを生成して投げる。
        // この例外は最終的にGlobalExceptionHandlerが捕まえ、HTTPステータス404としてレスポンスする。
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        // イベントが現在受付中でなければ（締切済み・開始前等）、ここで例外を投げる
        if (!event.isOpen(LocalDateTime.now())) {
            throw new BusinessException("申込受付は終了しました");
        }

        // 同一利用者が同一イベントに、有効な状態（受付済／キャンセル待ち）で既に申込済みかどうかを確認する
        boolean alreadyApplied = applicationRepository
                .existsByUser_IdAndEvent_IdAndStatusIn(userId, eventId, ApplicationStatus.ACTIVE_STATUSES);
        // 既に申込済みであれば、ここで例外を投げる（二重申込の防止）
        if (alreadyApplied) {
            throw new BusinessException("すでに申し込み済みです");
        }

        // 同時申込時の排他制御（悲観ロック）。定員判定から申込登録までの間、対象行
        // （区分があれば区分、無ければイベント）をロックし、同一対象への同時申込を直列化する。
        // ロックはこのメソッドのトランザクション終了（コミット）までDBが保持する。
        // 参加区分の存在・必須チェックを行い、対応する区分（区分の無いイベントならnull）を取得する
        TicketType ticketType = resolveTicketType(eventId, ticketTypeId);
        // 区分が無いイベントの場合は、ここでイベント行自体に悲観ロックをかける
        // （区分があるイベントの場合は、resolveTicketType()の中で区分行をロック済み）
        if (ticketType == null) {
            eventRepository.findByIdForUpdate(eventId);
        }
        // 定員を、区分があれば区分の定員、無ければイベント全体の定員とする
        int capacity = ticketType != null ? ticketType.getCapacity() : event.getCapacity();
        // 現在の受付済件数を、区分があれば区分単位、無ければイベント単位で数える
        long acceptedCount = ticketType != null
                ? applicationRepository.countByTicketType_IdAndStatus(ticketType.getId(), ApplicationStatus.ACCEPTED)
                : applicationRepository.countByEvent_IdAndStatus(eventId, ApplicationStatus.ACCEPTED);
        // 受付済件数が定員未満なら「受付済」、そうでなければ「キャンセル待ち」とする
        int status = acceptedCount < capacity ? ApplicationStatus.ACCEPTED : ApplicationStatus.WAITLISTED;

        // 存在確認済みのIDなのでDBに問い合わせない参照を使う（AuthInterceptorがuserIdを、上のfindByIdがeventを検証済み）
        // 申込エンティティを組み立てる
        Application application = new Application(
                userRepository.getReferenceById(userId), event, ticketType, status, extraAnswer);
        // DBに保存する（保存後、IDや申込日時が設定された状態のインスタンスが返る）
        Application saved = applicationRepository.save(application);

        // 申込の成功はログに出力しない（申込者・イベント・状況・日時はapplicationsの行から確認できるため。
        // docs/20_基本設計/24_方式設計書.md 6.2）

        // 保存結果をレスポンス用の形に変換して返す
        return new ApplicationResponse(
                saved.getId(),
                event.getId(),
                ticketType != null ? ticketType.getId() : null,
                userId,
                saved.getStatus(),
                codeNameResolver.statusName(saved.getStatus()),
                saved.getAppliedAt()
        );
    }

    // 区分存在・必須チェック（docs/30_詳細設計/33_共通詳細設計書.md E-B-004・E-B-005）。区分の無いイベントはticketTypeIdを無視する（docs/30_詳細設計/31_API詳細設計書.md AP-030）。
    // 区分ありイベントでは、対象区分の存在検証とあわせて悲観ロックを取得する（apply()参照）。
    private TicketType resolveTicketType(Long eventId, Long ticketTypeId) {
        // 対象イベントに参加区分が1件も存在しなければ、区分の無いイベントとしてnullを返す
        if (!ticketTypeRepository.existsByEvent_Id(eventId)) {
            return null;
        }
        // 区分があるイベントなのにticketTypeIdが指定されていなければ、ここで例外を投げる
        if (ticketTypeId == null) {
            throw new BusinessException("区分を選択してください");
        }
        // 指定されたticketTypeIdが対象イベントの区分として存在するかを確認し、存在すれば取得する
        // （findByIdAndEvent_IdForUpdateは取得と同時に悲観ロックもかける）
        return ticketTypeRepository.findByIdAndEvent_IdForUpdate(ticketTypeId, eventId)
                .orElseThrow(() -> new NotFoundException("指定された区分が見つかりません"));
    }

    // AP-031: 自分の申込一覧（申込日時降順）。本人分のみ返す＝userIdでの絞り込みそのもの
    /**
     * 利用者本人の申込一覧を取得する（AP-031）。ApplicationController#myApplicationsに加え、
     * UserController#applicationsOf（AP-142、管理者が他利用者を対象にする場合）からも共通で呼ばれる。
     * {@code @Transactional(readOnly = true)}は、更新を行わない参照専用の処理であることを表す指定で、
     * DBにその旨を伝えることで最適化が働く（更新系の{@code @Transactional}と区別するために付けている）。
     *
     * @param userId 対象の利用者ID
     * @return 申込一覧（キャンセル済みを含む全件、申込日時降順）
     */
    @Transactional(readOnly = true)
    public List<MyApplicationResponse> myApplications(Long userId) {
        // Stream API: リストに対して.stream()でストリーム（要素を順番に処理する流れ）を作り、
        // .map(...)で各要素を別の形（ここではMyApplicationResponse）に変換し、.toList()で
        // 結果を新しいListにまとめる、という一連の変換を1行で書く書き方。
        // this::toMyApplicationResponseは「このインスタンスのtoMyApplicationResponseメソッドを
        // 各要素に適用する」という意味で、ラムダ式（application -> toMyApplicationResponse(application)）の
        // 短縮形（メソッド参照）。
        return applicationRepository.findByUser_IdOrderByAppliedAtDesc(userId).stream()
                .map(this::toMyApplicationResponse)
                .toList();
    }

    // AP-032: 取消可否チェック（要件定義書§8）。本人の申込以外は403。
    // 受付済をキャンセルした場合、同一イベント（区分がある場合は同一区分）で最も古いキャンセル待ちを自動で繰り上げる。
    /**
     * 本人の申込をキャンセルする（AP-032）。ApplicationController#cancelから呼ばれる。
     * 他人の申込を指定した場合はForbiddenExceptionを投げ、GlobalExceptionHandlerにより403（Forbidden）になる。
     *
     * @param userId        キャンセルを実行する利用者ID（本人確認に使う）
     * @param applicationId キャンセル対象の申込ID
     */
    @Transactional
    public void cancel(Long userId, Long applicationId) {
        // キャンセル対象の申込を取得する（存在しなければ404になる例外を投げる）
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("申込が見つかりません"));

        // 申込の持ち主（利用者）がキャンセルを要求した本人と一致するかを確認する
        if (!application.getUser().getId().equals(userId)) {
            // 一致しなければ、ここで例外を投げる（他人の申込はキャンセルできない）
            throw new ForbiddenException("権限がありません");
        }

        // 「状況が受付済／キャンセル待ちのいずれかである」かつ「イベント開始前である」場合のみキャンセル可能とする
        boolean cancellable = ApplicationStatus.ACTIVE_STATUSES.contains(application.getStatus())
                && LocalDateTime.now().isBefore(application.getEvent().getStartAt());
        // キャンセルできない状態であれば、ここで例外を投げる
        if (!cancellable) {
            throw new BusinessException("取消できません");
        }

        // 繰り上げ処理が必要かどうかの判定に使うため、キャンセル前の状況が「受付済」だったかを覚えておく
        boolean wasAccepted = Integer.valueOf(ApplicationStatus.ACCEPTED).equals(application.getStatus());
        Long eventId = application.getEvent().getId();
        TicketType ticketType = application.getTicketType();
        // 申込自体の状況を「キャンセル済み」に変更する
        application.cancel();

        // キャンセル前が「受付済」だった場合のみ、キャンセル待ちの繰り上げ処理を行う
        if (wasAccepted) {
            // 同時申込時の排他制御（悲観ロック）。繰り上げ対象を探す前に、apply()と同じ行
            // （区分があれば区分、無ければイベント）をロックし、同時に走る申込・キャンセルと直列化する。
            // 区分があれば区分行、無ければイベント行に悲観ロックをかける（apply()と同じ対象）
            if (ticketType != null) {
                ticketTypeRepository.findByIdForUpdate(ticketType.getId());
            } else {
                eventRepository.findByIdForUpdate(eventId);
            }
            // 区分があれば同一区分、無ければ同一イベント（区分無し）の中で、最も申込日時が古い
            // 「キャンセル待ち」の申込を1件探す（無ければ空のOptional）
            Optional<Application> nextInLine = ticketType != null
                    ? applicationRepository.findFirstByTicketType_IdAndStatusOrderByAppliedAtAsc(
                            ticketType.getId(), ApplicationStatus.WAITLISTED)
                    : applicationRepository.findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(
                            eventId, ApplicationStatus.WAITLISTED);
            // 見つかった場合のみ、その申込を「受付済」に繰り上げる（Application::promoteは「あれば実行する」処理）
            nextInLine.ifPresent(Application::promote);
        }
        // キャンセル・繰り上げの成功はログに出力しない（applications.status_codeとupdated_atから確認できるため。
        // docs/20_基本設計/24_方式設計書.md 6.2）
    }

    // AP-125: 当日受付の申込者一覧（申込日時昇順、機能追加）。管理者権限はController側で確認済み
    /**
     * 当日受付画面で使う申込者一覧を取得する（AP-125）。EventController#attendeesから呼ばれる
     * （EventServiceではなくこちらに委譲されている点に注意）。管理者権限の確認はController側で完了済みのため、
     * ここでは行わない。
     *
     * @param eventId 対象イベントID
     * @return 申込者一覧（申込日時昇順、状況を問わず全件）
     */
    @Transactional(readOnly = true)
    public List<AttendeeResponse> listAttendees(Long eventId) {
        eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new NotFoundException("イベントが見つかりません"));

        return applicationRepository.findByEvent_IdOrderByAppliedAtAsc(eventId).stream()
                .map(this::toAttendeeResponse)
                .toList();
    }

    // AP-126: チェックイン可否チェック（要件定義書§8 E8、機能追加）。「受付済」以外は拒否
    /**
     * 当日受付でのチェックインを記録する（AP-126）。ApplicationController#checkInから呼ばれる
     * （管理者権限の確認はController側で完了済み）。
     *
     * @param applicationId チェックイン対象の申込ID
     * @return チェックイン結果（申込ID・チェックイン日時）
     */
    @Transactional
    public CheckInResponse checkIn(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("申込が見つかりません"));

        if (!Integer.valueOf(ApplicationStatus.ACCEPTED).equals(application.getStatus())) {
            throw new BusinessException("受付済の申込のみチェックインできます");
        }
        application.checkIn();

        // チェックインの実行者はapplicationsの行に残らないため、ログに記録する（docs/30_詳細設計/33_共通詳細設計書.md 6.1）
        log.info("チェックイン完了 applicationId={} 実行者userId={}",
                application.getId(), authContext.getCurrentUser().userId());

        return new CheckInResponse(application.getId(), application.getCheckedInAt());
    }

    private AttendeeResponse toAttendeeResponse(Application application) {
        return new AttendeeResponse(
                application.getId(),
                application.getUser().getName(),
                application.getTicketType() != null ? application.getTicketType().getName() : null,
                application.getStatus(),
                codeNameResolver.statusName(application.getStatus()),
                application.getCheckedInAt(),
                application.getExtraAnswer()
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
                codeNameResolver.statusName(application.getStatus()),
                application.getAppliedAt(),
                calculateWaitlistRank(application)
        );
    }

    // キャンセル待ちの順位計算（要件定義書§8）: 自分より申込日時が古い、同一イベント
    // （区分がある場合は同一区分）の「キャンセル待ち」件数＋1。キャンセル待ち以外はNULL
    private Long calculateWaitlistRank(Application application) {
        if (!Integer.valueOf(ApplicationStatus.WAITLISTED).equals(application.getStatus())) {
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
