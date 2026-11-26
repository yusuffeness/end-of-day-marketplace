// The orders of a business (GET /businesses/{id}/orders) by pickup day and status, the pickup verification
// (POST /businesses/{id}/pickups/verify) and the orders of today that were not picked up.
//
// The customer is shown as the API sends it ("Ahmet Y."); the API never sends the pickup code to the business, the
// customer tells it. The answer of the verification is not shown either: the lists are read again instead.

import { api, ApiError } from "../api.js";
import { businessPath, startBusinessPage } from "../business-context.js";
import { clear, el, hideMessage, loading, showError, showMessage } from "../dom.js";
import { formatDateTime, formatMoney, formatWindow, orderStatusLabel, todayIso } from "../format.js";
import { isAccessError, showAccessDenied, showPageError, startPage } from "../layout.js";
import { renderPager, table } from "../table.js";

const PAGE_SIZE = 20;

const pickerSlot = document.getElementById("business-picker");
const content = document.getElementById("business-content");

let businessId = null;
let page = 0;
let missedPage = 0;

function element(id) {
  return document.getElementById(id);
}

function itemsText(order) {
  return (order.items || []).map((item) => item.title + " × " + item.quantity).join(", ");
}

function ordersTable(orders) {
  return table(
    [
      { label: "Sipariş", value: (order) => "#" + order.id },
      { label: "Müşteri", value: (order) => order.customerName },
      { label: "Paketler", value: itemsText },
      { label: "Tutar", value: (order) => formatMoney(order.totalAmount), className: "number" },
      {
        label: "Teslim alma aralığı",
        value: (order) => (order.pickup ? formatWindow(order.pickup.windowStart, order.pickup.windowEnd) : ""),
      },
      {
        label: "Durum",
        value: (order) =>
          el("span", { className: "status status-" + order.status, text: orderStatusLabel(order.status) }),
      },
      {
        label: "Teslim / iptal zamanı",
        value: (order) =>
          order.pickup && order.pickup.pickedUpAt
            ? formatDateTime(order.pickup.pickedUpAt)
            : order.cancelledAt
              ? formatDateTime(order.cancelledAt)
              : "",
      },
    ],
    orders,
    (order) => "order-" + order.id,
  );
}

/** Loads one list into its container; resolves to false when the server refused the page. */
async function loadList({ list, message, pager, query, empty, onPage }) {
  hideMessage(message);
  loading(list);
  let result;
  try {
    result = await api.get(businessPath(businessId, "/orders"), { ...query, size: PAGE_SIZE });
  } catch (error) {
    clear(list);
    showError(message, error);
    return;
  }
  clear(list);
  if (result.items.length === 0) {
    showMessage(message, "info", empty);
  } else {
    list.append(ordersTable(result.items));
  }
  renderPager(pager, result, onPage);
}

function loadOrders() {
  const status = element("orders-status").value;
  return loadList({
    list: element("orders"),
    message: element("orders-message"),
    pager: element("orders-pager"),
    query: { date: element("orders-date").value || todayIso(), status, page },
    empty: status ? "Bu gün ve durumda sipariş yok." : "Bu gün için sipariş yok.",
    onPage: (next) => {
      page = next;
      loadOrders();
    },
  });
}

function loadMissed() {
  return loadList({
    list: element("missed"),
    message: element("missed-message"),
    pager: element("missed-pager"),
    query: { date: todayIso(), status: "TESLIM_ALINMADI", page: missedPage },
    empty: "Bugün teslim alınmayan sipariş yok.",
    onPage: (next) => {
      missedPage = next;
      loadMissed();
    },
  });
}

function bindVerifyForm() {
  const form = element("verify-form");
  const input = element("pickup-code");
  const button = form.querySelector('button[type="submit"]');
  const message = element("verify-message");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    // The button is locked by the first click; a second click while the request runs sends nothing.
    if (button.disabled) {
      return;
    }
    hideMessage(message);
    input.removeAttribute("aria-invalid");
    const code = input.value.trim();
    if (!/^\d{6}$/.test(code)) {
      input.setAttribute("aria-invalid", "true");
      showMessage(message, "error", "Teslim kodu 6 haneli olmalıdır.");
      return;
    }
    button.disabled = true;
    try {
      const order = await api.post(businessPath(businessId, "/pickups/verify"), { code });
      input.value = "";
      showMessage(message, "success", "Sipariş #" + order.id + " teslim edildi.");
      await Promise.all([loadOrders(), loadMissed()]);
    } catch (error) {
      showError(message, error);
    } finally {
      button.disabled = false;
    }
  });
}

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
  // The first request decides whether the page is shown: the server answers 403 for a business of another owner.
  try {
    await api.get(businessPath(businessId, "/orders"), { size: 1 });
  } catch (error) {
    if (isAccessError(error) || (error instanceof ApiError && error.status === 400)) {
      showAccessDenied(error);
      return;
    }
    clear(content);
    showPageError(error.detail || "Beklenmeyen bir hata oluştu.");
    return;
  }
  content.replaceChildren(element("orders-template").content.cloneNode(true));
  element("orders-date").value = todayIso();
  for (const id of ["orders-date", "orders-status"]) {
    element(id).addEventListener("change", () => {
      page = 0;
      loadOrders();
    });
  }
  bindVerifyForm();
  await Promise.all([loadOrders(), loadMissed()]);
}

await start();
