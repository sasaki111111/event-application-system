package com.example.eventapp.common.validation;

import com.example.eventapp.dto.EventUpsertRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// このvalidationパッケージは、Bean Validationの「カスタム制約アノテーション」というパターンを使っている。
// `@NotNull`や`@Pattern`のような標準の検証アノテーションだけでは表現できない独自の検証ルールを
// 自作する仕組みで、2つのファイルがセットで動く。
//   ・ValidEventDates（アノテーション側）: ルールの名前（@ValidEventDates）・適用先（クラス全体）・
//     検証失敗時のメッセージ・検証ロジックの実装クラスを宣言する。
//   ・EventDatesValidator（このクラス、検証ロジック側）: `ConstraintValidator<ValidEventDates, 対象の型>`を
//     実装し、isValid()メソッドに実際の検証処理を書く。
// `@Valid`でEventUpsertRequestを検証するとき、Spring（Bean Validation）がValidEventDatesの
// `validatedBy`で指定されたこのクラスを自動的に呼び出す。
/**
 * {@link ValidEventDates}アノテーションの実際の検証ロジック。
 * {@code ConstraintValidator<ValidEventDates, EventUpsertRequest>}を実装しているため、
 * {@code EventUpsertRequest}に{@code @ValidEventDates}を付けると、このクラスの{@link #isValid}が
 * 自動的に呼ばれる。
 */
public class EventDatesValidator implements ConstraintValidator<ValidEventDates, EventUpsertRequest> {

    /**
     * 検証本体。申込締切（applicationDeadline）が開催日時（startAt）以前であればOK（true）、
     * そうでなければNG（false）として検証エラーにする。
     */
    @Override
    public boolean isValid(EventUpsertRequest value, ConstraintValidatorContext context) {
        // 検証対象そのもの、またはstartAt／applicationDeadlineのいずれかがnullなら、
        // ここでは判定せずtrue（エラーにしない）を返す
        if (value == null || value.startAt() == null || value.applicationDeadline() == null) {
            // 必須チェック（@NotNull）側でエラーになるため、ここでは素通りさせる
            return true;
        }

        // 申込締切が開催日時より後（isAfter）でなければ、つまり「締切が開催日時以前」なら
        // ルールを満たしているのでtrue（OK）を返す
        if (!value.applicationDeadline().isAfter(value.startAt())) {
            return true;
        }

        // ここに到達するのは「締切が開催日時より後」の場合＝ルール違反。
        // デフォルトのエラー内容（クラス全体に対するエラー）をいったん無効化する
        // GlobalExceptionHandlerがfield名としてapplicationDeadlineを拾えるようにする
        context.disableDefaultConstraintViolation();
        // applicationDeadlineフィールドに対するエラーとして、新しい検証エラー（違反）を組み立てて登録する
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("applicationDeadline")
                .addConstraintViolation();
        // falseを返し、検証失敗（Bean Validationエラー）として扱わせる
        return false;
    }
}
