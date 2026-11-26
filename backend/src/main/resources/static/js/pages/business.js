// Business management for its owner: the business details (PUT /businesses/{id}), all listings of the business
// (GET /businesses/{id}/listings), a new listing (POST /businesses/{id}/listings), a new sale price
// (PUT .../listings/{listingId}/price) and deactivation (POST .../listings/{listingId}/deactivate).
//
// The form checks the obvious mistakes before sending; the server checks every rule again and has the last word.

import { api, ApiError } from "../api.js";
import { businessPath, startBusinessPage } from "../business-context.js";
import {
  clear,
  clearFieldErrors,
  el,
  hideMessage,
  loading,
  setFieldError,
  showError,
  showFieldErrors,
  showMessage,
} from "../dom.js";
import {
  activeLabel,
  approvalStatusLabel,
  businessTypeLabel,
  formatMoney,
  formatWindow,
  isFuture,
  listingStatusLabel,
  todayIso,
  toKurus,
} from "../format.js";
import { amountValue, requireValue, whileSubmitting } from "../form.js";
import { isAccessError, showAccessDenied, showPageError, startPage } from "../layout.js";
import { actionButton, renderPager, table } from "../table.js";

const PAGE_SIZE = 10;
const PHONE = /^[0-9+() -]{7,20}$/;

const pickerSlot = document.getElementById("business-picker");
const content = document.getElementById("business-content");

let businessId = null;
let business = null;
let page = 0;

function element(id) {
  return document.getElementById(id);
}

function text(id, value) {
  element(id).textContent = value;
}

// Business details

function fillBusiness() {
  const form = element("business-form");
  text("business-type", businessTypeLabel(business.type));
  text("business-approval", approvalStatusLabel(business.approvalStatus));
  text("business-active", activeLabel(business.active));
  for (const field of ["name", "city", "district", "address", "phone"]) {
    form.elements.namedItem(field).value = business[field] || "";
  }
  const open = business.approvalStatus === "ONAYLANDI" && business.active;
  element("listing-fields").disabled = !open;
  const reason = element("listing-disabled");
  if (open) {
    hideMessage(reason);
  } else {
    let why = "İlan açılamaz: ";
    if (business.approvalStatus === "BEKLEMEDE") {
      why += "işletme başvurunuz henüz onaylanmadı.";
    } else if (business.approvalStatus === "REDDEDILDI") {
      why += "işletme başvurunuz reddedildi.";
    } else {
      why += "işletme yönetici tarafından pasife alındı.";
    }
    showMessage(reason, "info", why);
  }
}

function bindBusinessForm() {
  const form = element("business-form");
  const message = element("business-message");
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    clearFieldErrors(form);
    hideMessage(message);
    let valid = [
      requireValue(form, "name", "İşletme adı zorunludur."),
      requireValue(form, "city", "Şehir zorunludur."),
      requireValue(form, "district", "İlçe zorunludur."),
      requireValue(form, "address", "Adres zorunludur."),
      requireValue(form, "phone", "Telefon zorunludur."),
    ].every(Boolean);
    const field = (name) => form.elements.namedItem(name).value.trim();
    if (field("phone") && !PHONE.test(field("phone"))) {
      setFieldError(form, "phone", "Geçerli bir telefon numarası giriniz.");
      valid = false;
    }
    if (!valid) {
      return;
    }
    whileSubmitting(form, async () => {
      try {
        business = await api.put(businessPath(businessId), {
          name: field("name"),
          city: field("city"),
          district: field("district"),
          address: field("address"),
          phone: field("phone"),
        });
        fillBusiness();
        showMessage(message, "success", "İşletme bilgileri kaydedildi.");
      } catch (error) {
        if (error instanceof ApiError) {
          showFieldErrors(form, error.fieldErrors);
        }
        showError(message, error);
      }
    });
  });
}

// New listing

function bindListingForm() {
  const form = element("listing-form");
  const message = element("listing-message");
  form.elements.namedItem("pickupDate").value = todayIso();
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    clearFieldErrors(form);
    hideMessage(message);
    const body = readListing(form);
    if (!body) {
      showMessage(message, "error", "Lütfen işaretli alanları düzeltin.");
      return;
    }
    whileSubmitting(form, async () => {
      try {
        const listing = await api.post(businessPath(businessId, "/listings"), body);
        form.reset();
        form.elements.namedItem("pickupDate").value = todayIso();
        showMessage(message, "success", "\"" + listing.title + "\" ilanı yayınlandı.");
        page = 0;
        await loadListings();
      } catch (error) {
        if (error instanceof ApiError) {
          showFieldErrors(form, error.fieldErrors);
        }
        showError(message, error);
      }
    });
  });
}

/** The request body of a new listing, or null after marking the fields that fail the client-side checks. */
function readListing(form) {
  const value = (name) => form.elements.namedItem(name).value.trim();
  let valid = [
    requireValue(form, "title", "Başlık zorunludur."),
    requireValue(form, "description", "Açıklama zorunludur."),
    requireValue(form, "allergenInfo", "Alerjen bilgisi zorunludur. Alerjen yoksa bunu belirtin."),
  ].every(Boolean);

  const normalPrice = amountValue(form, "normalPrice", "Normal fiyat");
  const salePrice = amountValue(form, "salePrice", "Satış fiyatı");
  if (normalPrice === null || salePrice === null) {
    valid = false;
  } else if (toKurus(salePrice) <= 0) {
    setFieldError(form, "salePrice", "Satış fiyatı sıfırdan büyük olmalıdır.");
    valid = false;
  } else if (toKurus(salePrice) >= toKurus(normalPrice)) {
    setFieldError(form, "salePrice", "Satış fiyatı normal fiyattan düşük olmalıdır.");
    valid = false;
  }

  const quantity = Number(value("totalQuantity"));
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 10000) {
    setFieldError(form, "totalQuantity", "Adet 1 ile 10000 arasında bir tam sayı olmalıdır.");
    valid = false;
  }

  const date = value("pickupDate");
  const start = value("pickupStart");
  const end = value("pickupEnd");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    setFieldError(form, "pickupDate", "Teslim günü zorunludur.");
    valid = false;
  }
  if (!/^\d{2}:\d{2}$/.test(start)) {
    setFieldError(form, "pickupStart", "Teslim başlangıcı zorunludur.");
    valid = false;
  }
  if (!/^\d{2}:\d{2}$/.test(end)) {
    setFieldError(form, "pickupEnd", "Teslim bitişi zorunludur.");
    valid = false;
  }
  if (!valid) {
    return null;
  }
  // One day and two times: the window always ends on the day it starts, so an end at or before the start (such as
  // 00:00) is rejected here instead of moving to the next day.
  const pickupStart = date + "T" + start + ":00";
  const pickupEnd = date + "T" + end + ":00";
  if (end <= start) {
    setFieldError(form, "pickupEnd", "Teslim bitişi başlangıçtan sonra ve aynı gün olmalıdır.");
    return null;
  }
  if (!isFuture(pickupStart)) {
    setFieldError(form, "pickupStart", "Teslim aralığı gelecekte başlamalıdır.");
    return null;
  }
  return {
    title: value("title"),
    description: value("description"),
    allergenInfo: value("allergenInfo"),
    normalPrice,
    salePrice,
    totalQuantity: quantity,
    pickupStart,
    pickupEnd,
  };
}

// Listings

function priceForm(listing, box) {
  const inputId = "price-" + listing.id;
  const input = el("input", {
    attrs: { id: inputId, name: "salePrice", type: "text", inputmode: "decimal", value: listing.salePrice },
  });
  const button = el("button", { className: "button button-small", text: "Fiyatı güncelle", attrs: { type: "submit" } });
  const form = el("form", { className: "price-form", attrs: { novalidate: true } }, [
    el("label", { className: "visually-hidden", text: "Yeni satış fiyatı (TL)", attrs: { for: inputId } }),
    input,
    button,
  ]);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    hideMessage(box);
    const salePrice = amountValue(form, "salePrice", "Satış fiyatı");
    if (salePrice === null) {
      showMessage(box, "error", "Geçerli bir satış fiyatı girin (en fazla 2 ondalık).");
      return;
    }
    if (toKurus(salePrice) <= 0 || toKurus(salePrice) >= toKurus(listing.normalPrice)) {
      showMessage(box, "error", "Satış fiyatı sıfırdan büyük ve normal fiyattan düşük olmalıdır.");
      return;
    }
    if (button.disabled) {
      return;
    }
    button.disabled = true;
    try {
      const updated = await api.put(businessPath(businessId, "/listings/" + listing.id + "/price"), { salePrice });
      await loadListings();
      showMessage(element("listings-message"), "success", "\"" + updated.title + "\" satış fiyatı " +
        formatMoney(updated.salePrice) + " olarak güncellendi.");
    } catch (error) {
      button.disabled = false;
      showError(box, error);
    }
  });
  return form;
}

function listingActions(listing) {
  if (listing.status !== "AKTIF") {
    return el("span", { className: "muted", text: "—" });
  }
  const box = el("div", { className: "message", attrs: { hidden: true } });
  const deactivate = actionButton("Pasife al", {
    kind: "danger",
    question: "\"" + listing.title + "\" ilanı pasife alınsın mı? Pasif ilan yeni sipariş almaz.",
    onClick: async () => {
      hideMessage(box);
      try {
        const updated = await api.post(businessPath(businessId, "/listings/" + listing.id + "/deactivate"));
        await loadListings();
        showMessage(element("listings-message"), "success", "\"" + updated.title + "\" ilanı pasife alındı.");
      } catch (error) {
        showError(box, error);
      }
    },
  });
  return el("div", { className: "cell-actions" }, [priceForm(listing, box), deactivate, box]);
}

async function loadListings() {
  const list = element("listings");
  const message = element("listings-message");
  const pager = element("listings-pager");
  hideMessage(message);
  loading(list);
  let result;
  try {
    result = await api.get(businessPath(businessId, "/listings"), {
      status: element("listing-status").value,
      page,
      size: PAGE_SIZE,
    });
  } catch (error) {
    clear(list);
    showError(message, error);
    return;
  }
  clear(list);
  if (result.items.length === 0) {
    showMessage(message, "info", element("listing-status").value ? "Bu durumda ilan yok." : "Henüz ilanınız yok.");
  } else {
    list.append(
      table(
        [
          { label: "İlan", value: (listing) => listing.title },
          { label: "Teslim aralığı", value: (listing) => formatWindow(listing.pickupStart, listing.pickupEnd) },
          { label: "Normal fiyat", value: (listing) => formatMoney(listing.normalPrice), className: "number" },
          { label: "Satış fiyatı", value: (listing) => formatMoney(listing.salePrice), className: "number" },
          {
            label: "Kalan / toplam",
            value: (listing) => listing.remainingQuantity + " / " + listing.totalQuantity,
            className: "number",
          },
          {
            label: "Durum",
            value: (listing) =>
              el("span", { className: "status status-" + listing.status, text: listingStatusLabel(listing.status) }),
          },
          { label: "İşlemler", value: listingActions },
        ],
        result.items,
        (listing) => "listing-" + listing.id,
      ),
    );
  }
  renderPager(pager, result, (next) => {
    page = next;
    loadListings();
  });
}

// Start

async function start() {
  if (!(await startPage())) {
    return;
  }
  let context;
  try {
    context = await startBusinessPage(pickerSlot, content);
  } catch (error) {
    clear(content);
    showPageError(error.detail || "Beklenmeyen bir hata oluştu.");
    return;
  }
  if (!context) {
    return;
  }
  businessId = context.id;
  business = context.business;
  // The first request decides whether the page is shown: the server answers 403 for a business of another owner.
  let first;
  try {
    first = await api.get(businessPath(businessId, "/listings"), { size: 1 });
  } catch (error) {
    if (business && business.approvalStatus !== "ONAYLANDI" && error instanceof ApiError && error.status === 403) {
      content.replaceChildren(el("div", { className: "message message-info", attrs: { role: "status" } }, [
        el("p", { text: business.name + ": " + approvalStatusLabel(business.approvalStatus) + "." }),
        el("p", { text: "İşletme yönetimi, başvurunuz yönetici tarafından onaylandığında açılır." }),
      ]));
      return;
    }
    if (isAccessError(error) || (error instanceof ApiError && error.status === 400)) {
      showAccessDenied(error);
      return;
    }
    clear(content);
    showPageError(error.detail || "Beklenmeyen bir hata oluştu.");
    return;
  }
  content.replaceChildren(element("business-template").content.cloneNode(true));
  if (business) {
    fillBusiness();
  } else if (first) {
    // Not in the first 100 businesses of the list: the details form stays hidden.
    element("business-form").hidden = true;
  }
  bindBusinessForm();
  bindListingForm();
  element("listing-status").addEventListener("change", () => {
    page = 0;
    loadListings();
  });
  await loadListings();
}

await start();
