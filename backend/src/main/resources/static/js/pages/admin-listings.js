// Listing inspection for the administrator (GET /admin/listings), including inactive and expired listings, and
// deactivating a listing that breaks the platform rules (POST /admin/listings/{id}/deactivate).

import { api } from "../api.js";
import { el, hideMessage, showError, showMessage } from "../dom.js";
import { formatMoney, formatWindow, listingStatusLabel } from "../format.js";
import { startPage } from "../layout.js";
import { listPage, showOutcome } from "../list-page.js";
import { actionButton } from "../table.js";

const filter = document.getElementById("filter");
const outcome = document.getElementById("outcome");

function value(name) {
  return filter.elements.namedItem(name).value.trim();
}

function deactivate(listing) {
  if (listing.status !== "AKTIF") {
    return null;
  }
  return actionButton("Pasife al", {
    kind: "danger",
    question: "\"" + listing.title + "\" (" + listing.businessName + ") ilanı pasife alınsın mı?",
    onClick: async () => {
      hideMessage(outcome);
      try {
        const updated = await api.post("/admin/listings/" + listing.id + "/deactivate");
        showOutcome(outcome, "success", "\"" + updated.title + "\" ilanı pasife alındı.");
        await list.reload();
      } catch (error) {
        showError(outcome, error);
      }
    },
  });
}

const list = listPage({
  list: document.getElementById("list"),
  message: document.getElementById("list-message"),
  pager: document.getElementById("pager"),
  fetch: (page) =>
    api.get("/admin/listings", {
      status: value("status"),
      businessId: value("businessId"),
      from: value("from"),
      to: value("to"),
      page,
      size: 20,
    }),
  rowId: (listing) => "listing-" + listing.id,
  empty: () => "Filtreye uyan ilan yok.",
  columns: [
    { label: "No", value: (listing) => listing.id, className: "number" },
    { label: "İlan", value: (listing) => listing.title },
    { label: "İşletme", value: (listing) => listing.businessName + " (" + listing.businessId + ")" },
    { label: "Alerjen bilgisi", value: (listing) => listing.allergenInfo },
    { label: "Teslim aralığı", value: (listing) => formatWindow(listing.pickupStart, listing.pickupEnd) },
    { label: "Satış fiyatı", value: (listing) => formatMoney(listing.salePrice), className: "number" },
    {
      label: "Kalan / toplam",
      value: (listing) => listing.remainingQuantity + " / " + listing.totalQuantity,
      className: "number",
    },
    {
      label: "Durum",
      value: (listing) =>
        el("span", { className: "status-" + listing.status, text: listingStatusLabel(listing.status) }),
    },
    { label: "İşlemler", value: deactivate },
  ],
});

filter.addEventListener("submit", (event) => {
  event.preventDefault();
  hideMessage(outcome);
  if (value("from") && value("to") && value("from") > value("to")) {
    showMessage(outcome, "error", "Başlangıç günü bitiş gününden sonra olamaz.");
    return;
  }
  list.load(0);
});

if (await startPage()) {
  await list.load(0);
}
