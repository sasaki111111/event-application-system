package com.example.eventapp.service;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.common.exception.UnauthorizedException;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。軽い会員登録・メールアドレスでのログイン・ユーザー一覧（いずれも機能追加）の業務ロジック。
// パスワードは扱わない。登録できるのは常に一般ユーザーのみ（管理者は登録経路を用意しない）。
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final AuthContext authContext;

    public UserService(UserRepository userRepository, AuthContext authContext) {
        this.userRepository = userRepository;
        this.authContext = authContext;
    }

    @Transactional
    public UserResponse register(String name, String email) {
        email = normalizeEmail(email);
        // 利用者列挙対策（docs/09_認証認可設計書.md 9-8）: 「既に登録済み」と直接断定せず、
        // ログインを促す形にする。管理者登録（registerAdmin）は管理者専用かつ利用者一覧から
        // 既存メールアドレスを確認できるため、こちらは明確なメッセージのままとする
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("この内容では登録できませんでした。ログインをお試しください");
        }

        User saved = userRepository.save(new User(name, email, "general"));
        log.info("利用者登録完了 userId={}", saved.getId());
        return toResponse(saved);
    }

    // AP-25: 管理者アカウント登録（管理者のみ。権限チェックはController側）。作成されるのは常に管理者
    @Transactional
    public UserResponse registerAdmin(String name, String email) {
        email = normalizeEmail(email);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("このメールアドレスは既に登録されています");
        }

        User saved = userRepository.save(new User(name, email, "admin"));
        log.info("管理者アカウント登録完了 userId={}（登録した管理者userId={}）",
                saved.getId(), authContext.getCurrentUser().userId());
        return toResponse(saved);
    }

    // POST /api/login: メールアドレスでユーザーを特定する（パスワード照合は無し＝ダミー認証のまま）
    @Transactional(readOnly = true)
    public UserResponse login(String email) {
        // 利用者列挙対策（docs/09_認証認可設計書.md 9-8）: 「未登録」と直接断定せず、新規登録を案内する形にする
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UnauthorizedException("ログインできませんでした。入力内容をご確認のうえ、初めてご利用の場合は新規登録してください"));
        return toResponse(user);
    }

    // メールアドレスの表記ゆれ対策（docs/07_バリデーション設計書.md 8-6）: 前後の空白を除去し小文字化してから
    // 重複チェック・検索・保存を行う。`eventapp`データベースの照合順序（utf8mb4_ja_0900_as_cs）は大文字小文字を
    // 区別するため、正規化しないと同じメールアドレスが別アカウントとして扱われてしまう。
    private String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    // GET /api/users: ユーザー一覧（管理者専用、権限チェックはController側）
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    // AP-26: 利用者詳細（SC-15）の表示対象利用者の基本情報。管理者専用、権限チェックはController側
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("利用者が見つかりません"));
        return toResponse(user);
    }

    // AP-33: 管理者権限の降格（管理者のみ。権限チェックはController側）。roleをgeneralに変更するのみ。
    // 管理者が1人のみの状態での降格は禁止する（docs/06_詳細設計書.md 7-5 R-17）
    @Transactional
    public UserResponse demote(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("利用者が見つかりません"));
        if (!user.isAdmin()) {
            throw new BusinessException("既に一般利用者です");
        }
        if (userRepository.countByRole("admin") <= 1) {
            throw new BusinessException("最後の管理者は降格できません");
        }
        user.demote();
        log.info("管理者権限降格完了 userId={}（実行した管理者userId={}）",
                user.getId(), authContext.getCurrentUser().userId());
        return toResponse(user);
    }

    // AP-34: 利用者の匿名化（退会）。本人、または管理者が実行できる（権限チェックはここで行う）。
    // 対象が管理者の場合は実行不可（先にAP-33で降格する必要がある、docs/06_詳細設計書.md 7-5 R-18）。
    // 物理削除ではなく、名前・メールアドレスを固定の文言・形式に置き換えるのみ（申込等の履歴は残す）
    @Transactional
    public UserResponse anonymize(Long id, Long requestingUserId, boolean requestingIsAdmin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("利用者が見つかりません"));
        boolean isSelf = requestingUserId.equals(id);
        if (!requestingIsAdmin && !isSelf) {
            throw new ForbiddenException("権限がありません");
        }
        if (user.isAdmin()) {
            throw new BusinessException("管理者は退会できません");
        }
        if (user.isAnonymized()) {
            throw new BusinessException("既に退会済みです");
        }
        user.anonymize();
        log.info("利用者匿名化完了 userId={}（実行した利用者userId={} 管理者={}）",
                user.getId(), requestingUserId, requestingIsAdmin);
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getAnonymizedAt());
    }
}
