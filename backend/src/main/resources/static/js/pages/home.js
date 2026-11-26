// Customer home page: the purchasable listings (GET /listings) with filters and pages. The filters and the page are
// kept in the query string of the address, so a reload or a shared link shows the same result.

import { api } from "../api.js";
import { clear, clearFieldErrors, el, hideMessage, loading, setFieldError, showError, showMessage } from "../dom.js";
import { businessTypeLabel, formatMoney, formatWindow } from "../format.js";
import { startPage } from "../layout.js";

const PAGE_SIZE = 12;
const FILTERS = ["city", "district", "type", "minPrice", "maxPrice"];
const PRICE = /^\d{1,8}([.,]\d{1,2})?$/;

const form = document.getElementById("filters");
const list = document.getElementById("listings");
const message = document.getElementById("listings-message");
const count = document.getElementById("result-count");
const pager = document.getElementById("pager");
const pageInfo = document.getElementById("page-info");
const previous = document.getElementById("previous-page");
const next = document.getElementById("next-page");

function currentQuery() {
  const params = new URLSearchParams(window.location.search);
  const query = {};
  for (const name of FILTERS) {
    const value = params.get(name);
    if (value) {
      query[name] = value;
    }
  }
  const page = Number.parseInt(params.get("page") || "0", 10);
  query.page = Number.isInteger(page) && page >= 0 ? page : 0;
  return query;
}

function fillForm(query) {
  for (const name of FILTERS) {
    form.elements.namedItem(name).value = query[name] || "";
  }
}

function writeAddress(query) {
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (value !== "" && value !== undefined && !(name === "page" && value === 0)) {
      params.set(name, String(value));
    }
  }
  const search = params.toString();
  window.history.pushState(null, "", window.location.pathname + (search ? "?" + search : ""));
}

/** Reads the form; returns null and marks the fields when a price is not a valid amount. */
function readForm() {
  clearFieldErrors(form);
  const query = {};
  let valid = true;
  for (const name of FILTERS) {
    const value = form.elements.namedItem(name).value.trim();
    if (!value) {
      continue;
    }
    if (name === "minPrice" || name === "maxPrice") {
      if (!PRICE.test(value)) {
        setFieldError(form, name, "Geçerli bir tutar giriniz, örneğin 50 veya 49,90.");
        valid = false;
        continue;
      }
      query[name] = value.replace(",", ".");
    } else {
      query[name] = value;
    }
  }
  if (valid && query.minPrice && query.maxPrice && Number(query.minPrice) > Number(query.maxPrice)) {
    setFieldError(form, "maxPrice", "En yüksek fiyat, en düşük fiyattan küçük olamaz.");
    valid = false;
  }
  return valid ? query : null;
}

function listingCard(listing) {
  const link = el("a", { text: listing.title, attrs: { href: "/listing.html?id=" + encodeURIComponent(listing.id) } });
  return el("li", { className: "card" }, [
    el("h2", { className: "card-title" }, [link]),
    el("p", { className: "card-business", text: listing.businessName + " · " + businessTypeLabel(listing.businessType) }),
    el("p", { className: "muted", text: listing.district + ", " + listing.city }),
    el("p", { className: "price" }, [
      el("span", { className: "price-sale", text: formatMoney(listing.salePrice) }),
      " ",
      el("s", { className: "price-normal", text: formatMoney(listing.normalPrice) }),
    ]),
    el("p", { text: "Kalan: " + listing.remainingQuantity + " adet" }),
    el("p", { className: "muted", text: "Teslim alma: " + formatWindow(listing.pickupStart, listing.pickupEnd) }),
  ]);
}

async function load() {
  const query = currentQuery();
  fillForm(query);
  hideMessage(message);
  count.hidden = true;
  pager.hidden = true;
  loading(list);
  let page;
  try {
    page = await api.get("/listings", { ...query, size: PAGE_SIZE });
  } catch (error) {
    clear(list);
    showError(message, error);
    return;
  }
  clear(list);
  if (page.items.length === 0) {
    showMessage(message, "info", page.totalItems > 0 ? "Bu sayfada paket yok." : "Şu an alınabilir paket yok.");
  }
  for (const listing of page.items) {
    list.append(listingCard(listing));
  }
  count.textContent = page.totalItems + " paket bulundu.";
  count.hidden = page.totalItems === 0;
  const pages = Math.max(1, Math.ceil(page.totalItems / page.size));
  pageInfo.textContent = "Sayfa " + (page.page + 1) + " / " + pages;
  previous.disabled = page.page === 0;
  next.disabled = page.page + 1 >= pages;
  pager.hidden = pages <= 1;
}

function goToPage(delta) {
  const query = currentQuery();
  query.page = Math.max(0, query.page + delta);
  writeAddress(query);
  load();
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  const query = readForm();
  if (query) {
    writeAddress(query);
    load();
  }
});

document.getElementById("reset-filters").addEventListener("click", () => {
  form.reset();
  clearFieldErrors(form);
  writeAddress({});
  load();
});

previous.addEventListener("click", () => goToPage(-1));
next.addEventListener("click", () => goToPage(1));
window.addEventListener("popstate", load);

if (await startPage()) {
  await load();
}
