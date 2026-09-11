package com.example.eventapp.common;

import java.util.Map;
import org.springframework.stereotype.Component;

// 実行環境: サーバー側（JVM）。B-5: ダミー認証用の固定ユーザー（要件定義書§2）。C-2でDBに同内容を投入後、
// D以降でRepository経由の参照に置き換える想定。
@Component
public class DummyUserStore {

    private final Map<Long, CurrentUser> users = Map.of(
            1L, new CurrentUser(1L, "一般ユーザー", "general"),
            2L, new CurrentUser(2L, "管理者", "admin")
    );

    public CurrentUser findById(Long userId) {
        return users.get(userId);
    }
}
