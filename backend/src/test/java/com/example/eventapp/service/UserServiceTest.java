package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CodeNameResolver;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.common.exception.UnauthorizedException;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.RoleCode;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * UserService（利用者登録・ログイン・管理者登録・降格・退会の業務ロジック）に対する単体テスト。
 * JUnit5とMockitoを使用する。UserRepository（DBアクセスを担うクラス）はモック化（偽装）し、
 * 本物のDBに接続せずに「メールアドレスの正規化」「最後の管理者は降格できない」等の業務ルールだけを検証する。
 */
// 実行環境: サーバー側（JVM）。docs/old/12_テスト仕様書.md 12-3 UT-USR-01〜17に対応する。
class UserServiceTest {

    private UserRepository userRepository;
    private AuthContext authContext;
    private UserService userService;

    // テストで使うパスワードと、そのハッシュ値。BCryptの計算は重いため、強度を下げたエンコーダで一度だけ計算する
    private static final String PASSWORD = "Test1234";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(4);
    private static final String PASSWORD_HASH = PASSWORD_ENCODER.encode(PASSWORD);

    private static final Long USER_ID = 5L;
    private static final Long OTHER_USER_ID = 6L;
    private static final Long ADMIN_ID = 2L;

    // 各@Testメソッドの実行前に毎回呼ばれ、テストごとに新しいモックとServiceを用意する
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authContext = mock(AuthContext.class);
        // 操作ログ（docs/30_詳細設計/33_共通詳細設計書.md）出力のため、registerAdmin()・demote()はログイン中管理者を参照する
        when(authContext.getCurrentUser()).thenReturn(new CurrentUser(ADMIN_ID, "管理者", RoleCode.ADMIN));
        userService = new UserService(userRepository, authContext, PASSWORD_ENCODER, codeNameResolver());
        // save()は渡されたUserをそのまま返す（DBが採番するIDは付かない）
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // テスト用のUserを組み立てるヘルパーメソッド。IDはDBが採番する項目でコンストラクタから設定できないため、
    // ReflectionTestUtils（テスト用にprivateフィールドへ値を入れる仕組み）で設定する。
    // パスワードはPASSWORD（Test1234）のハッシュ値を設定する。
    private User user(Long id, String name, String email, Integer roleCode) {
        User user = new User(name, email, PASSWORD_HASH, roleCode);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // 正常系（UT-USR-01）: 未登録のメールアドレスなら一般利用者として登録される
    @Test
    void register_正常系_未登録のメールアドレスなら一般利用者で登録される() {
        // このメールアドレスがまだ登録されていない状況を設定する
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        UserResponse response = userService.register("新規ユーザー", "new@example.com", PASSWORD);

        // save()に渡されたUserの利用者区分が一般利用者であることを確認する
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoleCode()).isEqualTo(RoleCode.GENERAL);
        // パスワードはそのまま保存されず、照合できるハッシュ値として保存されることを確認する
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(PASSWORD_ENCODER.matches(PASSWORD, captor.getValue().getPasswordHash())).isTrue();
        // レスポンスにも入力した名前・メールアドレスと利用者区分＝一般利用者が返ることを確認する
        assertThat(response.name()).isEqualTo("新規ユーザー");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.roleCode()).isEqualTo(RoleCode.GENERAL);
    }

    // 異常系（UT-USR-02）: 登録済みのメールアドレスは登録できない（文言は存在有無を断定しない）
    @Test
    void register_異常系_登録済みのメールアドレスは登録できない() {
        when(userRepository.existsByEmail("general@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("重複ユーザー", "general@example.com", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .hasMessage("この内容では登録できませんでした。ログインをお試しください");
        // 重複時は保存されないことを確認する
        verify(userRepository, never()).save(any());
    }

    // 異常系（UT-USR-03）: 大文字小文字・前後空白だけが異なるメールアドレスは、正規化後に重複と判定される
    @Test
    void register_異常系_大文字小文字と前後空白だけ異なるメールアドレスは重複と判定される() {
        // 正規化後（小文字・空白除去）のメールアドレスが登録済みである状況を設定する
        when(userRepository.existsByEmail("general@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("重複ユーザー", "  GENERAL@Example.com ", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .hasMessage("この内容では登録できませんでした。ログインをお試しください");
        // 重複チェックが正規化後のメールアドレスで行われたことを確認する
        verify(userRepository).existsByEmail("general@example.com");
        verify(userRepository, never()).save(any());
    }

    // 正常系（UT-USR-04）: 未登録のメールアドレスなら管理者として登録される
    @Test
    void registerAdmin_正常系_未登録のメールアドレスなら管理者で登録される() {
        when(userRepository.existsByEmail("newadmin@example.com")).thenReturn(false);

        UserResponse response = userService.registerAdmin("新管理者", "newadmin@example.com", PASSWORD);

        assertThat(response.roleCode()).isEqualTo(RoleCode.ADMIN);
        assertThat(response.email()).isEqualTo("newadmin@example.com");
    }

    // 異常系（UT-USR-05）: 管理者登録では、登録済みであることを明確に伝える文言になる
    @Test
    void registerAdmin_異常系_登録済みのメールアドレスは登録できない() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerAdmin("重複管理者", "admin@example.com", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .hasMessage("このメールアドレスは既に登録されています");
        verify(userRepository, never()).save(any());
    }

    // 正常系（UT-USR-06）: 大文字混在・前後空白ありでも、正規化して該当利用者を返す
    @Test
    void login_正常系_大文字混在と前後空白ありでも正規化して利用者を返す() {
        User admin = user(ADMIN_ID, "管理者", "admin@example.com", RoleCode.ADMIN);
        // 正規化後のメールアドレスでのみ利用者が見つかる状況を設定する
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        UserResponse response = userService.login("  ADMIN@example.com ", PASSWORD);

        assertThat(response.userId()).isEqualTo(ADMIN_ID);
        assertThat(response.roleCode()).isEqualTo(RoleCode.ADMIN);
    }

    // 異常系（UT-USR-07）: 未登録のメールアドレスではログインできない（文言は存在有無を断定しない）
    @Test
    void login_異常系_未登録のメールアドレスはログインできない() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login("unknown@example.com", PASSWORD))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("ログインできませんでした。入力内容をご確認のうえ、初めてご利用の場合は新規登録してください");
    }

    // 正常系（UT-USR-08）: 管理者が2人以上いれば、管理者を一般利用者に降格できる
    @Test
    void demote_正常系_管理者が2人以上いれば降格できる() {
        User target = user(USER_ID, "降格対象", "target@example.com", RoleCode.ADMIN);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));
        // 管理者が2人いる状況を設定する
        when(userRepository.countByRoleCode(RoleCode.ADMIN)).thenReturn(2L);

        UserResponse response = userService.demote(USER_ID);

        assertThat(response.roleCode()).isEqualTo(RoleCode.GENERAL);
        assertThat(target.isAdmin()).isFalse();
    }

    // 異常系（UT-USR-09）: 一般利用者は降格できない
    @Test
    void demote_異常系_一般利用者は降格できない() {
        User target = user(USER_ID, "一般ユーザー", "general@example.com", RoleCode.GENERAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.demote(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("既に一般利用者です");
    }

    // 異常系（UT-USR-10）: 管理者が対象の1人だけなら降格できない
    @Test
    void demote_異常系_最後の管理者は降格できない() {
        User target = user(ADMIN_ID, "管理者", "admin@example.com", RoleCode.ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(target));
        // 管理者が1人だけの状況を設定する
        when(userRepository.countByRoleCode(RoleCode.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.demote(ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("最後の管理者は降格できません");
        // 降格されず管理者のままであることを確認する
        assertThat(target.isAdmin()).isTrue();
    }

    // 異常系（UT-USR-11）: 存在しない利用者は降格できない
    @Test
    void demote_異常系_利用者が存在しなければNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.demote(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("利用者が見つかりません");
    }

    // 正常系（UT-USR-12）: 本人は自分のアカウントを退会（匿名化）できる
    @Test
    void anonymize_正常系_本人は自分を退会できる() {
        User target = user(USER_ID, "退会する人", "leave@example.com", RoleCode.GENERAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

        // 実行者＝対象者（本人）、管理者ではない
        UserResponse response = userService.anonymize(USER_ID, USER_ID, false);

        // 名前・メールアドレスが固定の文言・形式に置き換わり、退会日時が設定されることを確認する
        assertThat(response.name()).isEqualTo("退会済み利用者");
        assertThat(response.email()).isEqualTo("withdrawn-" + USER_ID + "@invalid.example");
        assertThat(response.anonymizedAt()).isNotNull();
    }

    // 正常系（UT-USR-13）: 管理者は一般利用者を退会させられる
    @Test
    void anonymize_正常系_管理者は一般利用者を退会させられる() {
        User target = user(USER_ID, "退会させられる人", "leave@example.com", RoleCode.GENERAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

        // 実行者は管理者（対象者とは別人）
        UserResponse response = userService.anonymize(USER_ID, ADMIN_ID, true);

        assertThat(response.name()).isEqualTo("退会済み利用者");
        assertThat(response.email()).isEqualTo("withdrawn-" + USER_ID + "@invalid.example");
        assertThat(response.anonymizedAt()).isNotNull();
    }

    // 権限（UT-USR-14）: 一般利用者は他人を退会させられない
    @Test
    void anonymize_権限_一般利用者は他人を退会させられない() {
        User target = user(USER_ID, "他人", "other@example.com", RoleCode.GENERAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

        // 実行者は別の一般利用者
        assertThatThrownBy(() -> userService.anonymize(USER_ID, OTHER_USER_ID, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("権限がありません");
        // 匿名化されていないことを確認する
        assertThat(target.isAnonymized()).isFalse();
        assertThat(target.getName()).isEqualTo("他人");
    }

    // 異常系（UT-USR-15）: 管理者は退会できない（先に降格が必要）
    @Test
    void anonymize_異常系_管理者は退会できない() {
        User target = user(ADMIN_ID, "管理者", "admin@example.com", RoleCode.ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.anonymize(ADMIN_ID, ADMIN_ID, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("管理者は退会できません");
        assertThat(target.isAnonymized()).isFalse();
    }

    // 異常系（UT-USR-16）: 退会済みの利用者は再度退会できない
    @Test
    void anonymize_異常系_退会済みの利用者は再度退会できない() {
        User target = user(USER_ID, "退会する人", "leave@example.com", RoleCode.GENERAL);
        // あらかじめ退会済みの状態にしておく
        target.anonymize();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.anonymize(USER_ID, ADMIN_ID, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("既に退会済みです");
    }

    // 異常系（UT-USR-17）: 存在しない利用者は退会できない
    @Test
    void anonymize_異常系_利用者が存在しなければNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.anonymize(USER_ID, ADMIN_ID, true))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("利用者が見つかりません");
    }

    // 区分値の表示名（コードマスタの内容）を返すCodeNameResolverのモックを組み立てるヘルパーメソッド。
    // 単体テストではDBに接続しないため、コードマスタの初期データと同じ対応をここで定義する
    private static CodeNameResolver codeNameResolver() {
        CodeNameResolver resolver = mock(CodeNameResolver.class);
        when(resolver.roleName(RoleCode.GENERAL)).thenReturn("一般利用者");
        when(resolver.roleName(RoleCode.ADMIN)).thenReturn("管理者");
        when(resolver.statusName(ApplicationStatus.ACCEPTED)).thenReturn("受付済");
        when(resolver.statusName(ApplicationStatus.WAITLISTED)).thenReturn("キャンセル待ち");
        when(resolver.statusName(ApplicationStatus.CANCELLED)).thenReturn("キャンセル済");
        return resolver;
    }

    // 異常系（R-22）: パスワードが一致しなければログインできない（未登録の場合と同じ文言）
    @Test
    void login_異常系_パスワードが一致しなければログインできない() {
        User admin = user(ADMIN_ID, "管理者", "admin@example.com", RoleCode.ADMIN);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.login("admin@example.com", "Wrong1234"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("ログインできませんでした。入力内容をご確認のうえ、初めてご利用の場合は新規登録してください");
    }

    // 異常系（R-22）: 退会済みの利用者はログインできない
    @Test
    void login_異常系_退会済みの利用者はログインできない() {
        User target = user(USER_ID, "退会する人", "leave@example.com", RoleCode.GENERAL);
        target.anonymize();
        // 退会後のメールアドレス（置き換え後の値）で検索された場合を想定する
        when(userRepository.findByEmail(target.getEmail())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.login(target.getEmail(), PASSWORD))
                .isInstanceOf(UnauthorizedException.class);
    }

    // 正常系（R-23）: 現在のパスワードが一致すれば、新しいパスワードのハッシュ値で上書きされる
    @Test
    void changePassword_正常系_現在のパスワードが一致すれば変更できる() {
        User target = user(USER_ID, "一般ユーザー", "general@example.com", RoleCode.GENERAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

        userService.changePassword(USER_ID, PASSWORD, "NewPass5678");

        // 新しいパスワードで照合でき、古いパスワードでは照合できなくなることを確認する
        assertThat(PASSWORD_ENCODER.matches("NewPass5678", target.getPasswordHash())).isTrue();
        assertThat(PASSWORD_ENCODER.matches(PASSWORD, target.getPasswordHash())).isFalse();
    }

    // 異常系（R-23）: 現在のパスワードが一致しなければ変更できない
    @Test
    void changePassword_異常系_現在のパスワードが一致しなければ変更できない() {
        User target = user(USER_ID, "一般ユーザー", "general@example.com", RoleCode.GENERAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.changePassword(USER_ID, "Wrong1234", "NewPass5678"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("現在のパスワードが正しくありません");
        // 拒否されたのでパスワードは変わっていないことを確認する
        assertThat(PASSWORD_ENCODER.matches(PASSWORD, target.getPasswordHash())).isTrue();
    }

    // 正常系（R-24）: 退会するとパスワードが無効化される
    @Test
    void anonymize_正常系_退会するとパスワードが無効化される() {
        User target = user(USER_ID, "退会する人", "leave@example.com", RoleCode.GENERAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

        userService.anonymize(USER_ID, USER_ID, false);

        assertThat(target.getPasswordHash()).isNull();
    }

    // seed.sqlに記載した初期利用者のパスワードハッシュが、初期パスワード（Test1234）と対応していることを確認する
    @Test
    void seed_初期利用者のパスワードハッシュは初期パスワードと照合できる() {
        String seedHash = "$2a$10$8Rn8vS9zkbWCMpHFdTrSveCAAUcB5T2srW0rYf.478IWwMuH3OTfO";

        assertThat(new BCryptPasswordEncoder().matches("Test1234", seedHash)).isTrue();
    }
}
