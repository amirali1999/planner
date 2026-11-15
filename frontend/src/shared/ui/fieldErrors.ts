/** Persian text for the backend's stable field codes (details values) and client-side checks. */
const FIELD_MESSAGES: Record<string, string> = {
  required: 'این فیلد الزامی است.',
  invalid_value: 'مقدار واردشده معتبر نیست.',
  too_long: 'متن واردشده بیش از حد طولانی است.',
  too_short: 'مقدار واردشده کوتاه است.',
  taken: 'این مقدار قبلاً استفاده شده است.',
  before_period_start: 'پایان بازه نباید قبل از شروع آن باشد.',
  out_of_range: 'تاریخ خارج از محدوده مجاز است.',
  parent_period_not_coarser: 'دوره هدف والد باید بزرگ‌تر از دوره این هدف باشد.',
  equals_baseline: 'مقدار هدف نباید با مقدار پایه برابر باشد.',
  must_be_positive: 'وزن باید بزرگ‌تر از صفر باشد.',
  precision_exceeded: 'حداکثر ۶ رقم اعشار و ۱۵ رقم صحیح مجاز است.',
  unknown_field: 'فیلد ناشناخته ارسال شد.',
  not_editable: 'این فیلد قابل ویرایش نیست.',
  required_for_recalibration: 'برای تغییر پایه، هدف یا وزن، دلیل تغییر را بنویسید.',
  invalid_date: 'تاریخ شمسی معتبر نیست.',
  incomplete_date: 'سال، ماه و روز را کامل وارد کنید.',
  invalid_number: 'یک عدد معتبر وارد کنید (مثلاً ۸۰ یا ۰٫۵).',
  invalid_time: 'ساعت معتبر نیست.',
  invalid_minutes: 'برآورد باید عدد صحیح نامنفی (دقیقه) باشد.',
  must_not_be_negative: 'مقدار نباید منفی باشد.',
  too_large: 'مقدار بیش از حد بزرگ است.',
};

export type FieldErrors = Record<string, string>;

export function fieldMessage(code: string | undefined): string | undefined {
  if (!code) return undefined;
  return FIELD_MESSAGES[code] ?? FIELD_MESSAGES.invalid_value;
}
