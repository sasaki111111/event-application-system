package com.example.eventapp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 「申込締切（applicationDeadline）は開催日時（startAt）より前」というこのプロジェクト独自の
 * 検証ルールを定義するアノテーション。実際の検証ロジックは{@link EventDatesValidator}
 * （{@code @Constraint(validatedBy = ...)}で指定）に実装されている。{@code EventUpsertRequest}
 * （クラス全体、{@code @Target(ElementType.TYPE)}）に付けて使う。
 */
// 実行環境: サーバー側（JVM）。「申込締切はstartAtより前」（docs/30_詳細設計/33_共通詳細設計書.md C-01）を@Validと連携させるための
// クラスレベルのBean Validation制約。EventUpsertRequestに付ける。
// このアノテーションをクラス全体（TYPE）に付けられるようにする
@Target(ElementType.TYPE)
// アプリの実行中（RUNTIME）までこのアノテーション情報を保持し、Bean Validationが読み取れるようにする
@Retention(RetentionPolicy.RUNTIME)
// 実際の検証ロジックはEventDatesValidatorクラスに実装されていることを指定する
@Constraint(validatedBy = EventDatesValidator.class)
public @interface ValidEventDates {

    // 検証に失敗したときに使われるデフォルトのエラーメッセージ
    String message() default "申込締切は開催日時より前にしてください";

    // Bean Validationの標準的な仕組み（検証グループ）用の属性。このプロジェクトでは未使用
    Class<?>[] groups() default {};

    // Bean Validationの標準的な仕組み（追加情報の受け渡し）用の属性。このプロジェクトでは未使用
    Class<? extends Payload>[] payload() default {};
}
