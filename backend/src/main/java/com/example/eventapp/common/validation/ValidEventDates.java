package com.example.eventapp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 実行環境: サーバー側（JVM）。「申込締切はstartAt以前」（API設計書 API-06/07）を@Validと連携させるための
// クラスレベルのBean Validation制約。EventUpsertRequestに付ける。
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventDatesValidator.class)
public @interface ValidEventDates {

    String message() default "申込締切は開催日時以前にしてください";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
