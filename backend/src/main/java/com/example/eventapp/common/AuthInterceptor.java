package com.example.eventapp.common;

import com.example.eventapp.common.exception.UnauthorizedException;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVCの{@code HandlerInterceptor}を実装したクラス。HandlerInterceptorとは、
 * ブラウザからのリクエストがController（実際の処理を行うクラス）に渡される前（または後）に
 * 割り込んで、共通の処理を行うための仕組み。ここでは「このリクエストを送ってきたのは誰か」を
 * 判定する認証処理を、個々のControllerに書かずにここ1か所にまとめている。
 * 実装するメソッドの1つが{@link #preHandle}で、ここがControllerの処理が始まる直前に必ず呼ばれる
 * 「関所」になる。{@code preHandle}が{@code false}を返すとControllerには到達せずそこで処理が終わる。
 * このインターセプターをどのURLパスに対して有効にするかは、WebConfigクラスの
 * {@code addInterceptors}メソッド側で設定している。
 */
// 実行環境: サーバー側（JVM）。Controllerの処理が始まる直前に必ず通る「関所」（HandlerInterceptor）。
// docs/20_基本設計/24_方式設計書.md: X-User-Idヘッダからログインユーザーを解決する（パスワードの照合はログイン時のみ行う）。
// ヘッダ無し／存在しないuserId／退会済み（匿名化済み）のuserIdは401（GlobalExceptionHandlerが変換）。
// どのURLに適用するか（/api/**、ただしlogin/pingは除外）はWebConfigで設定している。
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-User-Id";

    private final UserRepository userRepository;
    private final AuthContext authContext;

    public AuthInterceptor(UserRepository userRepository, AuthContext authContext) {
        // DI（依存性の注入）で受け取ったUserRepository・AuthContextをフィールドに保持する
        this.userRepository = userRepository;
        this.authContext = authContext;
    }

    /**
     * HandlerInterceptorのメソッド。Controllerの処理が実行される直前に、Spring MVCから自動的に
     * 呼び出される。戻り値が{@code true}ならController側の処理に進み、{@code false}ならここで
     * 処理を終了する（この場合、自分で{@code response}にステータスやエラー内容をセットするか、
     * 例外を投げてGlobalExceptionHandlerに処理を委ねる）。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // リクエストのHTTPメソッドがOPTIONSかどうかを文字列比較で判定する
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            // CORSプリフライトリクエストは認証ヘッダを付けずに送られるため素通りさせる
            return true;
        }

        // 軽い会員登録（機能追加）: POST /api/usersはログイン前に呼ばれるため認証不要。
        // GET /api/users（ユーザー一覧、管理者専用）はこの対象外＝通常通り認証・権限チェックされる。
        // HTTPメソッドがPOSTであり、かつリクエストのパスが"/api/users"と完全一致するかを判定する
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/users".equals(request.getRequestURI())) {
            return true;
        }

        // リクエストヘッダからX-User-Idの値を取り出す（送られていなければnullになる）
        String header = request.getHeader(HEADER_NAME);
        // ヘッダが無い、または空文字／空白のみの場合は未認証としてUnauthorizedException（401）を投げる
        if (header == null || header.isBlank()) {
            throw new UnauthorizedException("認証が必要です");
        }

        // ヘッダの値（文字列）を数値のユーザーIDに変換した結果を入れる変数を用意する
        Long userId;
        try {
            // 前後の空白を取り除いた上でLong型に変換する
            userId = Long.valueOf(header.trim());
        } catch (NumberFormatException ex) {
            // 数値に変換できない（数字以外の文字列が入っていた）場合も未認証として扱う
            throw new UnauthorizedException("認証が必要です");
        }

        // ユーザーIDに対応する利用者をDBから検索する。見つからなければnullを返す（orElse(null)）
        User user = userRepository.findById(userId).orElse(null);
        // 該当ユーザーが存在しない、または退会済み（匿名化済み）であれば認証エラーとする
        if (user == null || user.isAnonymized()) {
            // 退会済み（匿名化済み）の利用者は、存在しない利用者と同様に認証エラーとする
            // （AP-013による退会の効果を、継続中のアクセスにも及ぼすため）
            throw new UnauthorizedException("認証が必要です");
        }

        // 検証済みのユーザー情報をCurrentUserとして組み立て、AuthContextにセットする
        // （これ以降、同じリクエスト内のController/ServiceがAuthContext経由で参照できるようになる）
        authContext.setCurrentUser(new CurrentUser(user.getId(), user.getName(), user.getRoleCode()));
        // ここまで到達したら認証成功。trueを返してControllerの処理へ進む
        return true;
    }
}
