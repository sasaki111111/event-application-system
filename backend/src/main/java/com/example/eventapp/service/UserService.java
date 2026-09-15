package com.example.eventapp.service;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.UnauthorizedException;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。軽い会員登録・メールアドレスでのログイン・ユーザー一覧（いずれも機能追加）の業務ロジック。
// パスワードは扱わない。登録できるのは常に一般ユーザーのみ（管理者は登録経路を用意しない）。
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse register(String name, String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("このメールアドレスは既に登録されています");
        }

        User saved = userRepository.save(new User(name, email, "general"));
        return new UserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole());
    }

    // POST /api/login: メールアドレスでユーザーを特定する（パスワード照合は無し＝ダミー認証のまま）
    @Transactional(readOnly = true)
    public UserResponse login(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("そのメールアドレスは登録されていません"));
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    // GET /api/users: ユーザー一覧（管理者専用、権限チェックはController側）
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(user -> new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole()))
                .toList();
    }
}
