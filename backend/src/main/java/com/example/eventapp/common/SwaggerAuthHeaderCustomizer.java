package com.example.eventapp.common;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

// 実行環境: サーバー側（JVM）。Swagger UIでAPIを試す際、ダミー認証ヘッダ（X-User-Id）を
// Swagger UI画面上で直接入力できるようにするための共通設定。個別のControllerメソッドには手を加えない。
// 認証不要な3 API（AP-01ログイン、AP-02利用者登録、AP-24稼働確認）には追加しない
// （@Operationのsummaryに付けたAP番号で判定する）。
@Component
public class SwaggerAuthHeaderCustomizer implements OperationCustomizer {

    private static final Set<String> NO_AUTH_SUMMARY_PREFIXES = Set.of("AP-01 ", "AP-02 ", "AP-24 ");

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        String summary = operation.getSummary();
        boolean noAuthRequired = summary != null
                && NO_AUTH_SUMMARY_PREFIXES.stream().anyMatch(summary::startsWith);
        if (noAuthRequired) {
            return operation;
        }

        Parameter header = new Parameter()
                .in("header")
                .name("X-User-Id")
                .description("ダミー認証用の利用者ID（例: 1=一般ユーザー、2=管理者。db/seed.sqlの初期データに対応）")
                .required(true)
                .schema(new StringSchema());
        operation.addParametersItem(header);
        return operation;
    }
}
