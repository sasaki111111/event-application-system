package com.example.eventapp.common;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Swagger UI（ブラウザ上でAPIの動作を試せる画面）に表示される各APIの説明に、
 * ダミー認証用の入力欄（X-User-Idヘッダ）を自動的に追加するためのクラス。
 * {@code OperationCustomizer}を実装すると、Swagger UIの画面を生成するタイミングで
 * {@link #customize}メソッドが各API操作ごとに呼ばれ、画面の内容を加工できる。
 * これはSwagger UIの見た目を調整するだけの仕組みで、実際の認証処理（AuthInterceptor）には関係しない。
 */
// 実行環境: サーバー側（JVM）。Swagger UIでAPIを試す際、ダミー認証ヘッダ（X-User-Id）を
// Swagger UI画面上で直接入力できるようにするための共通設定。個別のControllerメソッドには手を加えない。
// 認証不要な3 API（AP-01ログイン、AP-02利用者登録、AP-24稼働確認）には追加しない
// （@Operationのsummaryに付けたAP番号で判定する）。
@Component
public class SwaggerAuthHeaderCustomizer implements OperationCustomizer {

    private static final Set<String> NO_AUTH_SUMMARY_PREFIXES = Set.of("AP-01 ", "AP-02 ", "AP-24 ");

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // このAPIの@Operation(summary = ...)に書かれているsummary文字列を取得する
        String summary = operation.getSummary();
        // summaryがNO_AUTH_SUMMARY_PREFIXES（"AP-01 "等）のいずれかで始まっていれば、
        // 認証不要なAPIだと判定する
        boolean noAuthRequired = summary != null
                && NO_AUTH_SUMMARY_PREFIXES.stream().anyMatch(summary::startsWith);
        if (noAuthRequired) {
            // 認証不要なAPIには入力欄を追加せず、そのまま返す
            return operation;
        }

        // Swagger UI上に表示する「X-User-Id」入力欄（ヘッダパラメータ）を1つ組み立てる
        Parameter header = new Parameter()
                .in("header")
                .name("X-User-Id")
                .description("ダミー認証用の利用者ID（例: 1=一般ユーザー、2=管理者。db/seed.sqlの初期データに対応）")
                .required(true)
                .schema(new StringSchema());
        // 組み立てた入力欄を、このAPI操作のパラメータ一覧に追加する
        operation.addParametersItem(header);
        // 加工済みのOperationを返す（Swagger UIの画面表示に使われる）
        return operation;
    }
}
