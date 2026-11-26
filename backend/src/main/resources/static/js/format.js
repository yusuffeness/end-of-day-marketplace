// Turkish labels and formats, kept in one place.

export const ORDER_STATUS_LABELS = Object.freeze({
  ONAYLANDI: "Onaylandı, teslim bekliyor",
  TESLIM_EDILDI: "Teslim edildi",
  IPTAL_EDILDI: "İptal edildi",
  TESLIM_ALINMADI: "Teslim alınmadı",
});

export const BUSINESS_TYPE_LABELS = Object.freeze({
  FIRIN: "Fırın",
  KAFE: "Kafe",
  RESTORAN: "Restoran",
});

export const ROLE_LABELS = Object.freeze({
  MUSTERI: "Müşteri",
  ISLETME_YETKILISI: "İşletme yetkilisi",
  YONETICI: "Yönetici",
});

export function orderStatusLabel(status) {
  return ORDER_STATUS_LABELS[status] || status;
}

export function businessTypeLabel(type) {
  return BUSINESS_TYPE_LABELS[type] || type;
}

const MONEY = new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" });

/** Amounts arrive as strings with two decimals ("120.00"); they are only converted to a number for display. */
export function formatMoney(amount) {
  if (amount === null || amount === undefined || amount === "") {
    return "";
  }
  return MONEY.format(Number(amount));
}

/** An amount string such as "60.00" as whole kuruş, so that totals are added without floating-point errors. */
export function toKurus(amount) {
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(String(amount));
  if (!match) {
    return 0;
  }
  return Number(match[1]) * 100 + Number((match[2] || "0").padEnd(2, "0"));
}

export function formatKurus(kurus) {
  return MONEY.format(kurus / 100);
}

const TIME_ZONE = "Europe/Istanbul";
const DATE_TIME = new Intl.DateTimeFormat("tr-TR", { timeZone: TIME_ZONE, dateStyle: "medium", timeStyle: "short" });
const TIME = new Intl.DateTimeFormat("tr-TR", { timeZone: TIME_ZONE, timeStyle: "short" });
const DATE = new Intl.DateTimeFormat("tr-TR", { timeZone: TIME_ZONE, dateStyle: "medium" });

/**
 * The API sends local Istanbul times without an offset ("2026-10-14T19:00:00"). Turkey keeps UTC+03:00 all year, so
 * the offset is added to get the real instant, which is then shown in the Europe/Istanbul time zone whatever the
 * time zone of the browser is.
 */
function instant(localDateTime) {
  return new Date(localDateTime + "+03:00");
}

export function formatDateTime(localDateTime) {
  return localDateTime ? DATE_TIME.format(instant(localDateTime)) : "";
}

/** "14 Eki 2026 19:00 – 21:00", or both full date-times when the window spans two days. */
export function formatWindow(start, end) {
  if (!start || !end) {
    return "";
  }
  if (start.slice(0, 10) === end.slice(0, 10)) {
    return DATE.format(instant(start)) + " " + TIME.format(instant(start)) + " – " + TIME.format(instant(end));
  }
  return formatDateTime(start) + " – " + formatDateTime(end);
}
