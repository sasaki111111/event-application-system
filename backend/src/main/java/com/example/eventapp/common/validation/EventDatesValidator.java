package com.example.eventapp.common.validation;

import com.example.eventapp.dto.EventUpsertRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// 実行環境: サーバー側（JVM）。ValidEventDatesの実際のチェック処理。
public class EventDatesValidator implements ConstraintValidator<ValidEventDates, EventUpsertRequest> {

    @Override
    public boolean isValid(EventUpsertRequest value, ConstraintValidatorContext context) {
        if (value == null || value.startAt() == null || value.applicationDeadline() == null) {
            // 必須チェック（@NotNull）側でエラーになるため、ここでは素通りさせる
            return true;
        }

        if (!value.applicationDeadline().isAfter(value.startAt())) {
            return true;
        }

        // GlobalExceptionHandlerがfield名としてapplicationDeadlineを拾えるようにする
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("applicationDeadline")
                .addConstraintViolation();
        return false;
    }
}
