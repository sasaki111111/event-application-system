package com.example.eventapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eventapp.common.RateLimitInterceptor;
import com.example.eventapp.dto.ApplicationCreateRequest;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.CommentModerationResponse;
import com.example.eventapp.dto.CountResponse;
import com.example.eventapp.dto.EventCommentCreateRequest;
import com.example.eventapp.dto.EventCommentResponse;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.dto.FavoriteCreateRequest;
import com.example.eventapp.dto.FavoriteEventResponse;
import com.example.eventapp.dto.FavoriteResponse;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.dto.UserCommentResponse;
import com.example.eventapp.dto.UserRegisterRequest;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.RoleCode;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventCommentRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.FavoriteRepository;
import com.example.eventapp.repository.TicketTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * REST APIを対象にした結合テスト（インテグレーションテスト）。
 * Controller→Service→Repository→DB（テスト実行時はH2インメモリDB）まで実際に処理を通し、
 * TestRestTemplateで本物のHTTPリクエストを送信し、レスポンスとDBの状態を合わせて検証する
 * （ServiceやRepositoryをモック化しない点が、Serviceクラス単体の単体テストとの違い）。
 * 各テストメソッド名の先頭「apNN」等は、対応する要件定義書の機能ID（例: AP-020）を示す。
 */
// 実行環境: サーバー側（JVM）。G-2: API/DB結合テスト。
// Controller→Service→Repository→H2（インメモリDB）まで実際に通し、必須API 8本のうち代表7本を検証する。
// Userは公開コンストラクタが無いためJdbcTemplateで直接INSERTし、Eventは実際のEventRepositoryで作成する。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ApiIntegrationTest {

    // テスト全体で使い回す、一般ユーザー・管理者それぞれのユーザーID（seed.sqlと同じ値）
    private static final Long GENERAL_USER_ID = 1L;
    private static final Long ADMIN_USER_ID = 2L;

    // テスト用の利用者に設定するパスワードと、そのハッシュ値（BCrypt）
    private static final String PASSWORD = "Test1234";
    // ログイン失敗時のメッセージ（E-A-006）。失敗の理由によらず同じ文言になる
    private static final String LOGIN_FAILED_MESSAGE = "ログインできませんでした";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    // @SpringBootTestがランダムに割り当てた実際のポート番号。TestRestTemplateでURLを組み立てる際に使う
    @LocalServerPort
    private int port;

    // 本物のHTTPリクエストを送信できるテスト用クライアント
    @Autowired
    private TestRestTemplate restTemplate;

    // 以下はDBの状態を直接確認・準備するために、本物のRepositoryをテストクラスに注入（@Autowired）したもの
    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private EventCommentRepository eventCommentRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    // Repositoryでは書きにくい生のSQL（users直接INSERT等）を実行するためのJdbcTemplate
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 回数制限（書き込み系APIは同一接続元から1分間に60回まで）の状態を、テストごとに初期化するために使う
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    // @BeforeEachが付いたメソッドは、このクラスの各@Testメソッドの実行前に毎回呼ばれる（テスト間でデータを独立させるための後始末）
    @BeforeEach
    void setUp() {
        // 前のテストまでの書き込み回数を引き継ぐと、後半のテストが429（リクエスト過多）になるため初期化する
        rateLimitInterceptor.reset();
        // 追加したお気に入り・コメント・参加区分関連テストがeventsを参照したまま残ると、
        // 後続テストのイベント削除がFK制約違反になるため先に消す
        // （子テーブル→親テーブルの順で削除し、外部キー制約に違反しないようにしている）
        favoriteRepository.deleteAll();
        eventCommentRepository.deleteAll();
        applicationRepository.deleteAll();
        ticketTypeRepository.deleteAll();
        eventRepository.deleteAll();
        // Userは公開コンストラクタが無いためRepository経由で作れず、JdbcTemplateで直接INSERTする
        jdbcTemplate.update("DELETE FROM users");
        // コードマスタ（利用者区分・申込状況）を初期データと同じ内容で用意する。
        // テスト環境（H2）はエンティティからテーブルを自動生成するため、seed.sqlは実行されない
        jdbcTemplate.update("DELETE FROM roles");
        jdbcTemplate.update("DELETE FROM application_statuses");
        jdbcTemplate.update("INSERT INTO roles (code, name, display_order) VALUES (1, '一般利用者', 1), (2, '管理者', 2)");
        jdbcTemplate.update("INSERT INTO application_statuses (code, name, display_order)"
                + " VALUES (1, '受付済', 1), (2, 'キャンセル待ち', 2), (9, 'キャンセル済', 3)");
        // テストで使う一般ユーザー（id=1）を登録する
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password_hash, role_code, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                GENERAL_USER_ID, "一般ユーザー", "general@example.com", PASSWORD_HASH, RoleCode.GENERAL);
        // テストで使う管理者（id=2）を登録する
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password_hash, role_code, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                ADMIN_USER_ID, "管理者", "admin@example.com", PASSWORD_HASH, RoleCode.ADMIN);
    }

    // 定員5のテスト用イベントを作るヘルパーメソッド（引数省略版）
    private Event openEvent() {
        return openEvent(5);
    }

    // 指定した定員でテスト用イベントを作り、実際にEventRepositoryで保存するヘルパーメソッド
    private Event openEvent(int capacity) {
        return eventRepository.save(new Event(
                "結合テスト用イベント",
                LocalDateTime.now().plusDays(10),
                "会議室",
                capacity,
                LocalDateTime.now().plusDays(5),
                "G-2結合テスト用データ",
                null, null, null));
    }

    // 指定したユーザーIDとしてAPIを呼ぶためのHTTPヘッダーを組み立てるヘルパーメソッド。
    // このアプリはX-User-Idヘッダで利用者を識別する（AuthInterceptor）ため、X-User-IdヘッダーだけでログインユーザーをAPI側に伝える
    private HttpHeaders authHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ランダムに割り当てられたポート番号を使って、呼び出し先のフルURLを組み立てるヘルパーメソッド
    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // AP-020: 登録したイベントがDBから読み出されて一覧に含まれる
    @Test
    void ap020_イベント一覧取得() {
        // 準備: テスト用イベントを1件、実際にDBへ保存する
        Event event = openEvent();

        // 実行: 一般ユーザーとしてイベント一覧APIを呼び出す
        ResponseEntity<EventSummaryResponse[]> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                EventSummaryResponse[].class);

        // 検証: レスポンスが200 OKで、一覧に先ほど作ったイベントのIDが含まれることを確認する
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(EventSummaryResponse::id).contains(event.getId());
    }

    // AP-021: 指定したイベントの詳細がDBの内容通りに返る
    @Test
    void ap021_イベント詳細取得() {
        Event event = openEvent();

        // 作成したイベントのIDを指定して詳細取得APIを呼び出す
        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events/" + event.getId()), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // イベント名がDBに保存した内容と一致することを確認する
        assertThat(response.getBody().name()).isEqualTo("結合テスト用イベント");
        // 定員5・申込0件なので、残り人数（remaining）が5であることを確認する
        assertThat(response.getBody().remaining()).isEqualTo(5);
    }

    // AP-120: 管理者が登録したイベントが実際にDBへ保存される
    @Test
    void ap120_管理者はイベントを登録できる() {
        // イベント登録APIに渡すリクエストボディを組み立てる
        EventUpsertRequest request = new EventUpsertRequest(
                "新規登録テスト", LocalDateTime.now().plusDays(20), "会議室B", 10,
                LocalDateTime.now().plusDays(15), null, null, null, null, null);

        // 管理者としてイベント登録APIを呼び出す
        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);

        // 201 Createdで返ることを確認する
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long createdId = response.getBody().id();
        // レスポンスのIDで、実際にDBから取得できる（＝本当に保存された）ことを確認する
        assertThat(eventRepository.findById(createdId)).isPresent();
        assertThat(eventRepository.findById(createdId).get().getName()).isEqualTo("新規登録テスト");
    }

    // AP-120の権限チェック: 一般ユーザーは登録できず、DBにも作られない
    @Test
    void ap120_一般ユーザーはイベントを登録できない() {
        EventUpsertRequest request = new EventUpsertRequest(
                "権限チェック用", LocalDateTime.now().plusDays(20), "会議室B", 10,
                LocalDateTime.now().plusDays(15), null, null, null, null, null);

        // 一般ユーザーとして（管理者専用の）イベント登録APIを呼び出す
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                String.class);

        // 権限が無いので403 Forbiddenになることを確認する
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 拒否されたのでDBにも1件も作られていないことを確認する
        assertThat(eventRepository.count()).isZero();
    }

    // AP-120（要件定義書§8）: 定員未指定でも、参加区分を指定していれば区分の定員合計で登録できる
    @Test
    void ap120_定員を指定せず参加区分のみで登録できる() {
        // capacity（4番目の引数）をnullにし、区分（一般枠7・会員枠3＝合計10）だけを指定したリクエストを作る
        EventUpsertRequest request = new EventUpsertRequest(
                "区分のみ登録テスト", LocalDateTime.now().plusDays(20), "会議室B", null,
                LocalDateTime.now().plusDays(15), null, null, null, null,
                List.of(new com.example.eventapp.dto.TicketTypeRequest("一般枠", 7),
                        new com.example.eventapp.dto.TicketTypeRequest("会員枠", 3)));

        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // capacityが区分の定員合計（7+3=10）で自動的に設定されたことを確認する
        assertThat(response.getBody().capacity()).isEqualTo(10);
    }

    // AP-120（要件定義書§8）: 定員も参加区分も指定が無ければ400（業務ルール違反）になる
    @Test
    void ap120_定員も参加区分も未指定なら400() {
        // capacityも区分（最後の引数）も指定しないリクエストを作る
        EventUpsertRequest request = new EventUpsertRequest(
                "定員無し登録テスト", LocalDateTime.now().plusDays(20), "会議室B", null,
                LocalDateTime.now().plusDays(15), null, null, null, null, null);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                String.class);

        // 業務ルール違反として400 Bad Requestになることを確認する
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // エラーメッセージに具体的な理由が含まれることを確認する
        assertThat(response.getBody()).contains("定員を入力してください");
    }

    // AP-030: 申込がDBに実際に1件作成される
    @Test
    void ap030_一般ユーザーは申込できる() {
        Event event = openEvent();
        ApplicationCreateRequest request = new ApplicationCreateRequest(event.getId(), null, null);

        // 一般ユーザーとして申込APIを呼び出す
        ResponseEntity<ApplicationResponse> response = restTemplate.exchange(
                url("/api/applications"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // DB上のapplicationsテーブルに実際に1件増えていることを確認する
        assertThat(applicationRepository.count()).isEqualTo(1);
    }

    // AP-031: 自分が申し込んだ内容がDBから読み出されて一覧に反映される
    @Test
    void ap031_自分の申込一覧取得() {
        Event event = openEvent();
        ApplicationCreateRequest applyRequest = new ApplicationCreateRequest(event.getId(), null, null);
        // まず1件申込んでおく（このテストの前提データ）
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(applyRequest, authHeaders(GENERAL_USER_ID)), ApplicationResponse.class);

        // 自分の申込一覧を取得するAPIを呼び出す
        ResponseEntity<MyApplicationResponse[]> response = restTemplate.exchange(
                url("/api/my/applications"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                MyApplicationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 1件申込んだので一覧も1件であることを確認する
        assertThat(response.getBody()).hasSize(1);
        // 一覧の内容が、先ほど申込んだイベントと一致することを確認する
        assertThat(response.getBody()[0].eventId()).isEqualTo(event.getId());
    }

    // AP-032: キャンセル後、DB上のステータスが実際に更新される
    @Test
    void ap032_申込をキャンセルできる() {
        Event event = openEvent();
        // 先に申込を1件作成し、そのレスポンスから申込IDを取り出す
        ResponseEntity<ApplicationResponse> applyResponse = restTemplate.exchange(
                url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);
        Long applicationId = applyResponse.getBody().id();

        // 作成した申込をキャンセルするAPIを呼び出す
        ResponseEntity<Void> cancelResponse = restTemplate.exchange(
                url("/api/applications/" + applicationId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), Void.class);

        // キャンセル成功時は204 No Contentであることを確認する
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // DBから申込を再取得し、ステータスが実際に「キャンセル済」に変わっていることを確認する
        Application cancelled = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    // 同時申込時の排他制御（悲観ロック）。定員1のイベントに2人が同時に申込んでも、
    // 「受付済」になるのは1件だけで、もう1件は「キャンセル待ち」になる（二重受付が起きない）ことを確認する。
    @Test
    void o01_同時に申し込んでも定員を超えて受付済にならない() throws Exception {
        // 定員1のイベントを用意する（2人が競合する状況を作るため、わざと定員を1にする）
        Event event = openEvent(1); // 定員1
        // 同時に申込ませる利用者2人（id=3, 4）を追加登録する
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password_hash, role_code, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                3L, "利用者3", "user3@example.com", PASSWORD_HASH, RoleCode.GENERAL);
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password_hash, role_code, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                4L, "利用者4", "user4@example.com", PASSWORD_HASH, RoleCode.GENERAL);

        // 2スレッドの実行環境（スレッドプール）を用意し、2人分の申込を本当に同時に実行させる
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // userId=3,4のそれぞれについて「申込APIを呼ぶ処理（Callable）」を作る
            List<Callable<ResponseEntity<ApplicationResponse>>> tasks = List.of(3L, 4L).stream()
                    .map(userId -> (Callable<ResponseEntity<ApplicationResponse>>) () -> restTemplate.exchange(
                            url("/api/applications"), HttpMethod.POST,
                            new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(userId)),
                            ApplicationResponse.class))
                    .collect(Collectors.toList());

            // invokeAll()で2つの申込処理を同時に実行し、両方の完了を待つ
            List<Future<ResponseEntity<ApplicationResponse>>> futures = pool.invokeAll(tasks);
            // 各申込結果からステータス文字列だけを取り出す
            List<Integer> statuses = futures.stream().map(f -> {
                try {
                    return f.get().getBody().statusCode();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            // 2件のうち、片方が「受付済」でもう片方が「キャンセル待ち」という組み合わせになることを確認する
            // （順序は実行タイミングで変わるため、containsExactlyInAnyOrderで順不同に比較する）
            assertThat(statuses).containsExactlyInAnyOrder(ApplicationStatus.ACCEPTED, ApplicationStatus.WAITLISTED);
            // DB上でも、このイベントの「受付済」件数が定員どおり1件だけであることを確認する
            assertThat(applicationRepository.countByEvent_IdAndStatus(event.getId(), ApplicationStatus.ACCEPTED))
                    .isEqualTo(1);
        } finally {
            // スレッドプールを必ず終了させる（後片付け）
            pool.shutdown();
        }
    }

    // AP-014: whoamiのレスポンスに、role="admin"由来のadminフィールド（true）が含まれる
    @Test
    void ap014_whoamiはadminフィールドを含む() {
        // 管理者として自分自身の情報を返すwhoami APIを呼び出す
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/whoami"), HttpMethod.GET, new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // レスポンスのJSON文字列に"admin":trueが含まれることを確認する（管理者であることが分かる項目）
        assertThat(response.getBody()).contains("\"admin\":true");
    }

    // AP-145: 既存の管理者は新たな管理者アカウントを登録でき、実際にDBへ管理者として保存される
    @Test
    void ap145_管理者は管理者アカウントを登録できる() {
        UserRegisterRequest request = new UserRegisterRequest("新管理者", "new-admin@example.com", PASSWORD);

        // 既存の管理者として、新しい管理者アカウントを登録するAPIを呼び出す
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // レスポンスのroleが"admin"であることを確認する
        assertThat(response.getBody().roleCode()).isEqualTo(RoleCode.ADMIN);
        // DBから直接SELECTしても、保存されたroleが"admin"であることを確認する
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role_code FROM users WHERE email = ?", Integer.class, "new-admin@example.com"))
                .isEqualTo(RoleCode.ADMIN);
    }

    // AP-145の権限チェック: 一般ユーザーは管理者アカウントを登録できない
    @Test
    void ap145_一般ユーザーは管理者アカウントを登録できない() {
        UserRegisterRequest request = new UserRegisterRequest("新管理者", "new-admin2@example.com", PASSWORD);

        // 一般ユーザーとして（管理者専用の）管理者登録APIを呼び出す
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 拒否されたので、該当メールアドレスのユーザーはDBに作られていないことを確認する
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class, "new-admin2@example.com")).isZero();
    }

    // AP-146: 管理者が2人以上いる状態なら、一方をもう一方が降格できる
    @Test
    void ap146_管理者は他の管理者を降格できる() {
        // 降格対象となる、2人目の管理者を先に作っておく
        UserRegisterRequest newAdmin = new UserRegisterRequest("新管理者", "new-admin3@example.com", PASSWORD);
        ResponseEntity<UserResponse> created = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST, new HttpEntity<>(newAdmin, authHeaders(ADMIN_USER_ID)),
                UserResponse.class);
        Long newAdminId = created.getBody().userId();

        // 既存の管理者（ADMIN_USER_ID）が、新しく作った管理者を降格するAPIを呼び出す
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/users/" + newAdminId + "/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // レスポンス上のroleが"general"に変わったことを確認する
        assertThat(response.getBody().roleCode()).isEqualTo(RoleCode.GENERAL);
        // DB上でも実際にroleが更新されていることを確認する
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role_code FROM users WHERE id = ?", Integer.class, newAdminId)).isEqualTo(RoleCode.GENERAL);
    }

    // AP-146の業務ルール（R-17）: 管理者が1人のみの状態では、その管理者を降格できない
    @Test
    void ap146_最後の管理者は降格できない() {
        // 管理者が1人（ADMIN_USER_IDのみ）の状態で、自分自身を降格しようとする
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + ADMIN_USER_ID + "/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        // 管理者が0人になってしまうため、400 Bad Requestで拒否されることを確認する
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // 拒否されたのでroleは変わっていないことを確認する
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role_code FROM users WHERE id = ?", Integer.class, ADMIN_USER_ID)).isEqualTo(RoleCode.ADMIN);
    }

    // AP-146の業務ルール（R-16）: 既に一般利用者の対象は降格できない
    @Test
    void ap146_既に一般利用者の対象は降格できない() {
        // すでに一般ユーザーであるGENERAL_USER_IDを降格しようとする
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // AP-146の権限チェック: 一般ユーザーは降格を実行できない
    @Test
    void ap146_一般ユーザーは降格を実行できない() {
        // 一般ユーザーとして（管理者専用の）降格APIを呼び出す
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + ADMIN_USER_ID + "/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 拒否されたので管理者のroleは変わっていないことを確認する
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role_code FROM users WHERE id = ?", Integer.class, ADMIN_USER_ID)).isEqualTo(RoleCode.ADMIN);
    }

    // AP-146: 対象の利用者が存在しない場合は404
    @Test
    void ap146_対象の利用者が存在しない場合は404() {
        // 存在しないユーザーID(9999)を指定して降格APIを呼び出す
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/9999/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // AP-013: 本人は自分のアカウントを退会（匿名化）できる
    @Test
    void ap013_本人は自分のアカウントを退会できる() {
        // 本人として自分自身の退会（削除）APIを呼び出す
        ResponseEntity<UserResponse> deleteResponse = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), UserResponse.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 退会日時（匿名化日時）が設定されたことを確認する
        assertThat(deleteResponse.getBody().anonymizedAt()).isNotNull();
        // 氏名が匿名化用の固定文言に置き換わることを確認する
        assertThat(deleteResponse.getBody().name()).isEqualTo("退会済み利用者");
        // DB上でもメールアドレスが退会済み用の形式に書き換わっていることを確認する
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, GENERAL_USER_ID))
                .isEqualTo("withdrawn-" + GENERAL_USER_ID + "@invalid.example");
    }

    // AP-013: 管理者は一般利用者を退会させられる
    @Test
    void ap013_管理者は一般利用者を退会させられる() {
        // 管理者として、一般ユーザーの退会APIを呼び出す
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().anonymizedAt()).isNotNull();
    }

    // AP-013の権限チェック: 一般利用者は他人のアカウントを退会させられない
    @Test
    void ap013_一般利用者は他人を退会させられない() {
        // 退会させようとする対象の、別の一般ユーザーを先に登録する
        UserRegisterRequest otherUser = new UserRegisterRequest("別の利用者", "other-user@example.com", PASSWORD);
        ResponseEntity<UserResponse> created = restTemplate.exchange(
                url("/api/users"), HttpMethod.POST, new HttpEntity<>(otherUser), UserResponse.class);
        Long otherUserId = created.getBody().userId();

        // 一般ユーザー（GENERAL_USER_ID）として、別の利用者（otherUserId）を退会させようとする
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + otherUserId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 拒否されたので、対象ユーザーの匿名化日時はまだNULL（退会していない）ことを確認する
        assertThat(jdbcTemplate.queryForObject(
                "SELECT anonymized_at IS NULL FROM users WHERE id = ?", Boolean.class, otherUserId)).isTrue();
    }

    // AP-013の業務ルール（R-18）: 管理者は退会できない（先にAP-146で降格する必要がある）
    @Test
    void ap013_管理者は退会できない() {
        // 管理者が自分自身を退会させようとする
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + ADMIN_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT anonymized_at IS NULL FROM users WHERE id = ?", Boolean.class, ADMIN_USER_ID)).isTrue();
    }

    // AP-013の業務ルール（R-19）: 既に退会済みの利用者を再度退会させることはできない
    @Test
    void ap013_既に退会済みの利用者は再度退会できない() {
        // 1回目の退会を実行する（これは成功するはず）
        restTemplate.exchange(url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), UserResponse.class);

        // 同じユーザーに対して、管理者から2回目の退会を試みる
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        // 既に退会済みのため、2回目は400 Bad Requestになることを確認する
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // AP-013: 対象の利用者が存在しない場合は404
    @Test
    void ap013_対象の利用者が存在しない場合は404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/9999"), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // AP-013実行後の認証: 退会済み（匿名化済み）のuserIdは、以後のリクエストで認証エラーになる
    // （AuthInterceptorがAP-013の効果を継続中のアクセスにも及ぼす）
    @Test
    void ap013_退会済みの利用者は以後のリクエストで認証エラーになる() {
        // 本人が退会する
        restTemplate.exchange(url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), UserResponse.class);

        // 退会したはずの同じユーザーIDで、別のAPI（whoami）を呼んでみる
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/whoami"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        // 退会済みのユーザーとしては認証されず、401 Unauthorizedになることを確認する
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // AP-122 format=csv: 削除済みイベントに紐づく申込明細はCSVに含まれない
    @Test
    void ap122_csv出力は削除済みイベントの申込を含まない() {
        Event deletedEvent = openEvent();
        ApplicationCreateRequest applyRequest = new ApplicationCreateRequest(deletedEvent.getId(), null, null);
        // このイベントに申込を1件作っておく
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(applyRequest, authHeaders(GENERAL_USER_ID)), ApplicationResponse.class);
        // そのイベント自体をソフトデリート（論理削除）する
        deletedEvent.softDelete();
        eventRepository.save(deletedEvent);

        // CSV形式で申込一覧を出力するAPIを呼び出す
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/applications?format=csv"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 削除済みイベントの名前がCSV本文に含まれていない（除外されている）ことを確認する
        assertThat(response.getBody()).doesNotContain(deletedEvent.getName());
    }

    // AP-020: favoriteCountがお気に入り登録件数を反映する。全利用者に返す項目のため一般ユーザーで確認する
    @Test
    void ap020_favoriteCountはお気に入り登録件数を反映する() {
        Event event = openEvent();
        // このイベントをお気に入り登録する
        restTemplate.exchange(url("/api/favorites"), HttpMethod.POST,
                new HttpEntity<>(new FavoriteCreateRequest(event.getId()), authHeaders(GENERAL_USER_ID)),
                FavoriteResponse.class);

        // イベント一覧を取得する
        ResponseEntity<EventSummaryResponse[]> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                EventSummaryResponse[].class);

        // 一覧の中から対象イベントだけを絞り込み、favoriteCountが1件になっていることを確認する
        assertThat(response.getBody())
                .filteredOn(e -> e.id().equals(event.getId()))
                .extracting(EventSummaryResponse::favoriteCount)
                .containsExactly(1L);
    }

    // AP-130: CSV明細にアンケート回答列が追加され、回答内容がそのまま出力される
    @Test
    void ap130_csv出力にアンケート回答列が含まれる() {
        // アンケート（extra_question）付きのイベントを作る
        Event event = eventRepository.save(new Event(
                "アンケート付きイベント",
                LocalDateTime.now().plusDays(10),
                "会議室",
                5,
                LocalDateTime.now().plusDays(5),
                "G-2結合テスト用データ",
                null, null, "参加動機を教えてください"));
        // アンケートに回答しつつ申込む
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, "業務で必要なため"),
                        authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/applications?format=csv"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // CSVの見出しに「アンケート回答」列があることを確認する
        assertThat(response.getBody()).contains("アンケート回答");
        // 回答した内容がそのままCSVに出力されていることを確認する
        assertThat(response.getBody()).contains("業務で必要なため");
    }

    // AP-141: 管理者は利用者情報を取得できる
    @Test
    void ap141_管理者は利用者情報を取得できる() {
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("一般ユーザー");
    }

    // AP-141: 一般ユーザーは利用者情報を取得できない（403）
    @Test
    void ap141_一般ユーザーは利用者情報を取得できない() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + ADMIN_USER_ID), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // AP-141: 存在しない利用者の情報は取得できない（404）
    @Test
    void ap141_存在しない利用者の情報は取得できない() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/9999"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // AP-142: 管理者は対象利用者の申込一覧を取得できる。一般ユーザーは403
    @Test
    void ap142_利用者の申込一覧取得() {
        Event event = openEvent();
        // 一般ユーザーが1件申込んでおく
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        // 管理者が、対象ユーザーの申込一覧を取得するAPIを呼び出す
        ResponseEntity<MyApplicationResponse[]> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/applications"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), MyApplicationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].eventId()).isEqualTo(event.getId());

        // 一般ユーザー自身が同じAPI（他人の申込一覧用）を呼ぶと、権限が無く拒否されることを確認する
        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/applications"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // AP-143: 管理者は対象利用者のお気に入り一覧を取得できる
    @Test
    void ap143_利用者のお気に入り一覧取得() {
        Event event = openEvent();
        restTemplate.exchange(url("/api/favorites"), HttpMethod.POST,
                new HttpEntity<>(new FavoriteCreateRequest(event.getId()), authHeaders(GENERAL_USER_ID)),
                FavoriteResponse.class);

        // 管理者が、対象ユーザーのお気に入り一覧を取得するAPIを呼び出す
        ResponseEntity<FavoriteEventResponse[]> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/favorites"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), FavoriteEventResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].id()).isEqualTo(event.getId());
    }

    // AP-130: CSV明細に参加区分列が追加され、区分名がそのまま出力される
    @Test
    void ap130_csv出力に参加区分列が含まれる() {
        Event event = eventRepository.save(new Event(
                "区分付きイベント",
                LocalDateTime.now().plusDays(10),
                "会議室",
                5,
                LocalDateTime.now().plusDays(5),
                "G-2結合テスト用データ",
                null, null, null));
        // 更新APIで「一般枠（定員5）」という参加区分を追加する
        ResponseEntity<EventDetailResponse> updated = restTemplate.exchange(
                url("/api/events/" + event.getId()), HttpMethod.PUT,
                new HttpEntity<>(new EventUpsertRequest(
                        event.getName(), event.getStartAt(), event.getPlace(), 5, event.getApplicationDeadline(),
                        event.getDescription(), null, null, null,
                        List.of(new com.example.eventapp.dto.TicketTypeRequest("一般枠", 5))),
                        authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);
        // 更新結果から、追加された区分のIDを取り出す
        Long ticketTypeId = updated.getBody().ticketTypes().get(0).id();

        // 取り出した区分IDを指定して申込む
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), ticketTypeId, null),
                        authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/applications?format=csv"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // CSVの見出しに「参加区分」列があることを確認する
        assertThat(response.getBody()).contains("参加区分");
        // 区分名「一般枠」がそのままCSVに出力されていることを確認する
        assertThat(response.getBody()).contains("一般枠");
    }

    // AP-131: 管理者はお気に入りの総数を取得できる
    @Test
    void ap131_管理者はお気に入り総数を取得できる() {
        Event event = openEvent();
        restTemplate.exchange(url("/api/favorites"), HttpMethod.POST,
                new HttpEntity<>(new FavoriteCreateRequest(event.getId()), authHeaders(GENERAL_USER_ID)),
                FavoriteResponse.class);

        ResponseEntity<CountResponse> response = restTemplate.exchange(
                url("/api/favorites/count"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), CountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().count()).isEqualTo(1L);
    }

    // AP-131: 一般ユーザーはお気に入りの総数を取得できない（403）
    @Test
    void ap131_一般ユーザーはお気に入り総数を取得できない() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/favorites/count"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // AP-132: コメント総数は論理削除済みを除外し、管理者のみ実行できる
    @Test
    void ap132_コメント総数取得は論理削除済みを除外する() {
        Event event = openEvent();
        // 1件目: 後で削除される予定のコメント（投稿内容はコメントIDを後で使うためだけの目的）
        ResponseEntity<EventCommentResponse> commentResponse = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("有効なコメント", null), authHeaders(GENERAL_USER_ID)),
                EventCommentResponse.class);
        // 2件目: 削除されない、有効なコメント
        restTemplate.exchange(url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("削除予定のコメント", null), authHeaders(GENERAL_USER_ID)),
                EventCommentResponse.class);
        Long willBeDeletedId = commentResponse.getBody().id();
        // 返信を付けたうえで削除すると論理削除になる
        restTemplate.exchange(url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("返信", willBeDeletedId), authHeaders(ADMIN_USER_ID)),
                EventCommentResponse.class);
        // 1件目のコメントを削除する（返信があるため論理削除になる）
        restTemplate.exchange(url("/api/comments/" + willBeDeletedId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), Void.class);

        // コメント総数（論理削除済みを除く）を取得するAPIを呼び出す
        ResponseEntity<CountResponse> response = restTemplate.exchange(
                url("/api/comments/count"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), CountResponse.class);

        // 「削除予定のコメント」「返信」の2件が有効。論理削除された1件は含まない
        assertThat(response.getBody().count()).isEqualTo(2L);
    }

    // AP-144: 利用者のコメント履歴は、論理削除済みも含めイベント名付きで取得できる
    @Test
    void ap144_利用者のコメント履歴取得() {
        Event event = openEvent();
        restTemplate.exchange(url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("履歴確認用コメント", null), authHeaders(GENERAL_USER_ID)),
                EventCommentResponse.class);

        // 管理者が、対象ユーザーのコメント履歴を取得するAPIを呼び出す
        ResponseEntity<UserCommentResponse[]> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/comments"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), UserCommentResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        // 投稿先のイベント名が付与されていることを確認する
        assertThat(response.getBody()[0].eventName()).isEqualTo(event.getName());
        // まだ削除していないのでdeletedがfalseであることを確認する
        assertThat(response.getBody()[0].deleted()).isFalse();

        // 一般ユーザー自身が同じAPIを呼ぶと権限が無く拒否されることを確認する
        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/comments"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // AP-150: 全コメント一覧は有効なコメントのみを対象とし、イベント名・投稿者名を含む
    @Test
    void ap150_全コメント一覧取得は有効なコメントのみ() {
        Event event = openEvent();
        ResponseEntity<EventCommentResponse> commentResponse = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("表示されるコメント", null), authHeaders(GENERAL_USER_ID)),
                EventCommentResponse.class);
        Long commentId = commentResponse.getBody().id();
        // このコメントへの返信を投稿する
        restTemplate.exchange(url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("返信", commentId), authHeaders(ADMIN_USER_ID)),
                EventCommentResponse.class);
        // 返信があるので論理削除される＝一覧には「削除されました」として残るが、本テストは有効なものだけ数える
        restTemplate.exchange(url("/api/comments/" + commentId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), Void.class);

        // 全コメント一覧（モデレーション用）を取得するAPIを呼び出す
        ResponseEntity<CommentModerationResponse[]> response = restTemplate.exchange(
                url("/api/comments"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), CommentModerationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 「表示されるコメント」は論理削除済みのため対象外、「返信」のみが有効なコメントとして残る
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].body()).isEqualTo("返信");
        assertThat(response.getBody()[0].eventName()).isEqualTo(event.getName());
        assertThat(response.getBody()[0].userName()).isEqualTo("管理者");

        // 一般ユーザーが同じAPIを呼ぶと権限が無く拒否されることを確認する
        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/comments"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // AP-010（Issue #9の再発防止）: メールアドレスの前後に空白・大文字が混ざっていても、正規化してログインできる。
    // DTO（LoginRequest）をテスト側で生成すると送信前に空白が除去されてしまうため、Mapで生のJSONを送る
    @Test
    void ap010_前後に空白のあるメールアドレスでもログインできる() {
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "  ADMIN@example.com ", "password", PASSWORD), authHeaders(ADMIN_USER_ID)),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 空白除去＋小文字化したメールアドレスの利用者（管理者）が返ることを確認する
        assertThat(response.getBody().userId()).isEqualTo(ADMIN_USER_ID);
    }

    // AP-011（Issue #9の再発防止）: 前後に空白のあるメールアドレスは、空白除去＋小文字化して登録される
    @Test
    void ap011_前後に空白のあるメールアドレスは正規化して登録される() {
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/users"), HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "空白確認", "email", " New-User@Example.com ", "password", PASSWORD), authHeaders(GENERAL_USER_ID)),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().email()).isEqualTo("new-user@example.com");
        // DBにも正規化後のメールアドレスで保存されていることを確認する
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, "new-user@example.com"))
                .isEqualTo(1);
    }

    // AP-011（Issue #9の再発防止）: 登録済みのメールアドレスに空白を付けただけのものは、重複として拒否される
    @Test
    void ap011_登録済みのメールアドレスに空白を付けても重複として拒否される() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users"), HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "重複確認", "email", " GENERAL@example.com ", "password", PASSWORD), authHeaders(GENERAL_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // 形式エラーではなく、重複時のメッセージになることを確認する
        assertThat(response.getBody()).contains("この内容では登録できませんでした。ログインをお試しください");
    }

    // AP-145（Issue #9の再発防止）: 管理者アカウント登録でも、前後に空白のあるメールアドレスを正規化して登録できる
    @Test
    void ap145_前後に空白のあるメールアドレスでも管理者アカウントを登録できる() {
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "空白確認管理者", "email", " Space-Admin@Example.com ", "password", PASSWORD), authHeaders(ADMIN_USER_ID)),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().roleCode()).isEqualTo(RoleCode.ADMIN);
        assertThat(response.getBody().email()).isEqualTo("space-admin@example.com");
    }

    // AP-120（Issue #10の再発防止）: 申込締切が開催日時と同じ日時のイベントは登録できない（締切は開催日時より前であること）
    @Test
    void ap120_申込締切と開催日時が同じ日時なら400() {
        LocalDateTime sameTime = LocalDateTime.now().plusDays(20).withNano(0);
        EventUpsertRequest request = new EventUpsertRequest(
                "締切同時刻テスト", sameTime, "会議室B", 10, sameTime, null, null, null, null, null);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // 申込締切の項目に対する入力エラーとして返ることを確認する
        assertThat(response.getBody()).contains("\"field\":\"applicationDeadline\"");
        // イベントが登録されていないことを確認する
        assertThat(eventRepository.count()).isZero();
    }

    // AP-120（Issue #10の補足）: 申込締切が開催日時の1秒前なら、これまでどおり登録できる
    @Test
    void ap120_申込締切が開催日時の1秒前なら登録できる() {
        LocalDateTime startAt = LocalDateTime.now().plusDays(20).withNano(0);
        EventUpsertRequest request = new EventUpsertRequest(
                "締切1秒前テスト", startAt, "会議室B", 10, startAt.minusSeconds(1), null, null, null, null, null);

        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // AP-121（Issue #10の再発防止）: 申込締切が開催日時と同じ日時になる更新はできない
    @Test
    void ap121_申込締切と開催日時が同じ日時には更新できない() {
        Event event = openEvent();
        LocalDateTime sameTime = LocalDateTime.now().plusDays(20).withNano(0);
        EventUpsertRequest request = new EventUpsertRequest(
                "更新後の名前", sameTime, "会議室", 5, sameTime, null, null, null, null, null);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events/" + event.getId()), HttpMethod.PUT, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"field\":\"applicationDeadline\"");
        // イベントが更新されていないことを確認する
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getName()).isEqualTo("結合テスト用イベント");
    }

    // AP-121（Issue #15の再発防止）: 区分のあるイベントを、既存と同じ区分名のまま更新できる。
    // 編集画面は区分を変更していなくても既存の区分を毎回送るため、これが失敗すると画面から編集できなくなる
    @Test
    void ap121_既存と同じ区分名を送り直しても更新できる() {
        EventUpsertRequest create = new EventUpsertRequest(
                "区分付きイベント", LocalDateTime.now().plusDays(20), "会議室B", null,
                LocalDateTime.now().plusDays(15), null, null, null, null,
                List.of(new com.example.eventapp.dto.TicketTypeRequest("午前", 2),
                        new com.example.eventapp.dto.TicketTypeRequest("午後", 3)));
        Long eventId = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(create, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class).getBody().id();

        // 場所だけを変更し、区分は既存と同じ名前・定員のまま送る
        EventUpsertRequest update = new EventUpsertRequest(
                "区分付きイベント", LocalDateTime.now().plusDays(20), "会議室C", 5,
                LocalDateTime.now().plusDays(15), null, null, null, null,
                List.of(new com.example.eventapp.dto.TicketTypeRequest("午前", 2),
                        new com.example.eventapp.dto.TicketTypeRequest("午後", 3)));
        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events/" + eventId), HttpMethod.PUT, new HttpEntity<>(update, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().place()).isEqualTo("会議室C");
        assertThat(response.getBody().ticketTypes()).extracting(t -> t.name()).containsExactlyInAnyOrder("午前", "午後");
        assertThat(response.getBody().capacity()).isEqualTo(5);
    }

    // AP-121（Issue #15の再発防止）: 区分名を一部残したまま、区分を追加・定員変更する更新ができる
    @Test
    void ap121_区分名を一部残したまま区分を変更できる() {
        EventUpsertRequest create = new EventUpsertRequest(
                "区分付きイベント", LocalDateTime.now().plusDays(20), "会議室B", null,
                LocalDateTime.now().plusDays(15), null, null, null, null,
                List.of(new com.example.eventapp.dto.TicketTypeRequest("午前", 2),
                        new com.example.eventapp.dto.TicketTypeRequest("午後", 3)));
        Long eventId = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(create, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class).getBody().id();

        // 「午前」は名前を残して定員を変更、「午後」を外して「夜」を追加する
        EventUpsertRequest update = new EventUpsertRequest(
                "区分付きイベント", LocalDateTime.now().plusDays(20), "会議室B", null,
                LocalDateTime.now().plusDays(15), null, null, null, null,
                List.of(new com.example.eventapp.dto.TicketTypeRequest("午前", 4),
                        new com.example.eventapp.dto.TicketTypeRequest("夜", 1)));
        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events/" + eventId), HttpMethod.PUT, new HttpEntity<>(update, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().ticketTypes()).extracting(t -> t.name()).containsExactlyInAnyOrder("午前", "夜");
        assertThat(response.getBody().capacity()).isEqualTo(5);
    }

    // AP-010: 正しいメールアドレスとパスワードでログインでき、利用者区分がコードと表示名の両方で返る
    @Test
    void ap010_正しいパスワードでログインでき利用者区分のコードと表示名が返る() {
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "admin@example.com", "password", PASSWORD)), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().userId()).isEqualTo(ADMIN_USER_ID);
        assertThat(response.getBody().roleCode()).isEqualTo(RoleCode.ADMIN);
        // 表示名はコードマスタ（roles）から取得した値であることを確認する
        assertThat(response.getBody().roleName()).isEqualTo("管理者");
    }

    // AP-010（R-22）: パスワードが一致しない場合は401になり、応答にパスワードが含まれない
    @Test
    void ap010_パスワードが一致しなければ401になる() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "admin@example.com", "password", "Wrong1234")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains(LOGIN_FAILED_MESSAGE);
        // 応答にパスワードやハッシュ値が含まれないことを確認する
        assertThat(response.getBody()).doesNotContain("Wrong1234").doesNotContain("password");
    }

    // AP-010（R-22）: メールアドレスが未登録の場合も、パスワードの不一致と同じ401・同じメッセージになる
    // （どちらの理由で失敗したかを区別できないようにするため）
    @Test
    void ap010_未登録のメールアドレスはパスワード不一致と同じ401になる() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "nobody@example.com", "password", PASSWORD)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains(LOGIN_FAILED_MESSAGE);
    }

    // AP-010（E-V-024）: パスワードが未入力の場合は入力エラー（400）になる
    @Test
    void ap010_パスワード未入力は400になる() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "admin@example.com")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"field\":\"password\"");
    }

    // AP-011（E-V-025）: パスワードが8文字未満の場合は登録できない
    @Test
    void ap011_8文字未満のパスワードでは登録できない() {
        assertWeakPasswordRejected("Short1");
    }

    // AP-011（E-V-025）: パスワードが英字だけの場合は登録できない
    @Test
    void ap011_英字だけのパスワードでは登録できない() {
        assertWeakPasswordRejected("onlyletters");
    }

    // AP-011（E-V-025）: パスワードが数字だけの場合は登録できない
    @Test
    void ap011_数字だけのパスワードでは登録できない() {
        assertWeakPasswordRejected("12345678");
    }

    // 条件を満たさないパスワードで利用者登録を試み、400で拒否され、利用者が登録されていないことを確認する
    private void assertWeakPasswordRejected(String weakPassword) {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users"), HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "弱いパスワード", "email", "weak@example.com", "password", weakPassword)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, "weak@example.com")).isZero();
    }

    // AP-011: 登録時、パスワードはそのままではなくハッシュ化した値で保存される
    @Test
    void ap011_パスワードはハッシュ化して保存される() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users"), HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "新規利用者", "email", "hash-check@example.com", "password", PASSWORD)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // レスポンスにパスワード・ハッシュ値が含まれないことを確認する
        assertThat(response.getBody()).doesNotContain(PASSWORD).doesNotContain("password");
        String stored = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, "hash-check@example.com");
        assertThat(stored).isNotEqualTo(PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, stored)).isTrue();
    }

    // AP-012（R-23）: 現在のパスワードが正しければ変更でき、保存されているハッシュ値が変わる
    @Test
    void ap012_現在のパスワードが正しければ変更できる() {
        String before = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, GENERAL_USER_ID);

        ResponseEntity<Void> change = changePassword(PASSWORD, "NewPass5678");

        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String after = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, GENERAL_USER_ID);
        assertThat(after).isNotEqualTo(before);
    }

    // AP-012: パスワードの変更後は、新しいパスワードでログインできる
    @Test
    void ap012_変更後は新しいパスワードでログインできる() {
        changePassword(PASSWORD, "NewPass5678");

        ResponseEntity<String> login = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "general@example.com", "password", "NewPass5678")), String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // AP-012: パスワードの変更後は、古いパスワードではログインできない
    @Test
    void ap012_変更後は古いパスワードでログインできない() {
        changePassword(PASSWORD, "NewPass5678");

        ResponseEntity<String> login = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "general@example.com", "password", PASSWORD)), String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // 一般ユーザー（GENERAL_USER_ID）としてパスワード変更APIを呼び出すヘルパーメソッド
    private ResponseEntity<Void> changePassword(String currentPassword, String newPassword) {
        return restTemplate.exchange(
                url("/api/my/password"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("currentPassword", currentPassword, "newPassword", newPassword),
                        authHeaders(GENERAL_USER_ID)), Void.class);
    }

    // AP-012（E-B-023）: 現在のパスワードが一致しない場合は変更できない
    @Test
    void ap012_現在のパスワードが一致しなければ変更できない() {
        ResponseEntity<String> change = restTemplate.exchange(
                url("/api/my/password"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("currentPassword", "Wrong1234", "newPassword", "NewPass5678"),
                        authHeaders(GENERAL_USER_ID)), String.class);

        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(change.getBody()).contains("現在のパスワードが正しくありません");
        // 拒否されたので、元のパスワードのままログインできることを確認する
        ResponseEntity<String> login = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "general@example.com", "password", PASSWORD)), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // AP-012: 未ログイン（X-User-Id無し）ではパスワードを変更できない
    @Test
    void ap012_未ログインではパスワードを変更できない() {
        ResponseEntity<String> change = restTemplate.exchange(
                url("/api/my/password"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("currentPassword", PASSWORD, "newPassword", "NewPass5678")), String.class);

        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // AP-013（R-24）: 退会するとパスワードが無効化され、退会前のメールアドレス・パスワードではログインできない
    @Test
    void ap013_退会後は元のメールアドレスとパスワードでログインできない() {
        restTemplate.exchange(url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        ResponseEntity<String> login = restTemplate.exchange(
                url("/api/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "general@example.com", "password", PASSWORD)), String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, GENERAL_USER_ID)).isNull();
    }

    // AP-030・AP-031: 申込状況がコードと表示名（コードマスタの値）の両方で返る
    @Test
    void ap030_申込状況がコードと表示名の両方で返る() {
        Event event = openEvent(1);
        ResponseEntity<ApplicationResponse> applied = restTemplate.exchange(
                url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        assertThat(applied.getBody().statusCode()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(applied.getBody().statusName()).isEqualTo("受付済");

        ResponseEntity<MyApplicationResponse[]> mine = restTemplate.exchange(
                url("/api/my/applications"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                MyApplicationResponse[].class);
        assertThat(mine.getBody()[0].statusCode()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(mine.getBody()[0].statusName()).isEqualTo("受付済");
    }
}
