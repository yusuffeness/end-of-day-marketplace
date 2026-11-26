// The sales summary shown by the business report and the platform report: summary boxes, a table of the figures and
// the rating distribution as plain CSS bars.

import { el } from "./dom.js";
import { formatDate, formatMoney } from "./format.js";
import { table } from "./table.js";

function box(label, value, note) {
  return el("div", { className: "stat" }, [
    el("span", { className: "stat-label", text: label }),
    el("span", { className: "stat-value", text: value }),
    note ? el("span", { className: "stat-note", text: note }) : null,
  ]);
}

function ratingText(report) {
  return report.averageRating === null ? "Değerlendirme yok" : report.averageRating.replace(".", ",") + " / 5";
}

/**
 * The nodes of a report. extraBoxes and extraRows are [label, value] pairs added to the summary boxes and to the table
 * of figures, for example the platform figures.
 */
export function reportView(report, extraBoxes = [], extraRows = []) {
  const boxes = el("div", { className: "stats" }, [
    box("Satılan paket", String(report.unitsSold)),
    box("İptal edilen sipariş", String(report.cancelledCount)),
    box("Net tahsilat", formatMoney(report.netRevenue)),
    box("Ortalama puan", ratingText(report), report.ratingCount + " değerlendirme"),
    ...extraBoxes.map(([label, value]) => box(label, value)),
  ]);
  const rows = [
    ["Tarih aralığı", formatDate(report.from) + " – " + formatDate(report.to)],
    ["Sipariş sayısı", String(report.orderCount)],
    ["Teslim edilen sipariş", String(report.deliveredCount)],
    ["İptal edilen sipariş", String(report.cancelledCount)],
    ["Teslim alınmayan sipariş", String(report.notPickedUpCount)],
    ["Satılan paket adedi", String(report.unitsSold)],
    ["Brüt tahsilat", formatMoney(report.grossRevenue)],
    ["İade edilen", formatMoney(report.refundedAmount)],
    ["Net tahsilat", formatMoney(report.netRevenue)],
    ["Değerlendirme sayısı", String(report.ratingCount)],
    ["Ortalama puan", ratingText(report)],
    ...extraRows,
  ];
  const figures = table(
    [
      { label: "Gösterge", value: (row) => row[0] },
      { label: "Değer", value: (row) => row[1], className: "number" },
    ],
    rows,
  );
  return [
    boxes,
    el("section", { className: "panel" }, [el("h2", { text: "Özet" }), figures]),
    el("section", { className: "panel" }, [el("h2", { text: "Puan dağılımı" }), ratingBars(report)]),
  ];
}

function ratingBars(report) {
  if (report.ratingCount === 0) {
    return el("p", { className: "muted", text: "Bu aralıkta değerlendirme yok." });
  }
  const list = el("dl", { className: "bars" });
  for (const rating of ["5", "4", "3", "2", "1"]) {
    const count = report.ratingDistribution[rating] || 0;
    const share = Math.round((count / report.ratingCount) * 100);
    const fill = el("span", { className: "bar-fill" });
    fill.style.width = share + "%";
    list.append(
      el("dt", { text: rating + " puan" }),
      el("dd", {}, [
        el("span", { className: "bar", attrs: { "aria-hidden": "true" } }, [fill]),
        el("span", { className: "bar-count", text: count + " (%" + share + ")" }),
      ]),
    );
  }
  return list;
}
