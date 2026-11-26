// The customer's orders (GET /orders) with cancellation (POST /orders/{id}/cancel) and review
// (POST /orders/{id}/review). The buttons follow canCancel and canReview of the API; the rules are not repeated here
// and the server has the last word.

import { api, ApiError } from "../api.js";
import { clear, el, hideMessage, loading, showError, showMessage } from "../dom.js";
import { formatDateTime, formatMoney, formatWindow, orderStatusLabel } from "../format.js";
import { startPage } from "../layout.js";

const PAGE_SIZE = 10;
const CANCEL_KEY = "eod.cancel.";

const list = document.getElementById("orders");
const message = document.getElementById("orders-message");
const statusSelect = document.getElementById("status");
const pager = document.getElementById("pager");
const pageInfo = document.getElementById("page-info");
const previous = document.getElementById("previous-page");
const next = document.getElementById("next-page");

let page = 0;

/** The request id of a cancellation attempt is kept per order until the server has answered it. */
function cancelRequestId(orderId) {
  const key = CANCEL_KEY + orderId;
  try {
    const stored = window.sessionStorage.getItem(key);
    if (stored) {
      return stored;
    }
    const created = window.crypto.randomUUID();
    window.sessionStorage.setItem(key, created);
    return created;
  } catch {
    return window.crypto.randomUUID();
  }
}

function forgetCancelRequestId(orderId) {
  try {
    window.sessionStorage.removeItem(CANCEL_KEY + orderId);
  } catch {
    // Nothing to forget when the storage is blocked.
  }
}

function detail(label, value, className) {
  return [el("dt", { text: label }), el("dd", { text: value, className })];
}

function orderItem(order) {
  const box = el("div", { className: "message", attrs: { hidden: true } });
  const details = el("dl", { className: "details" }, [
    ...detail("Durum", orderStatusLabel(order.status), "status status-" + order.status),
    ...detail("Sipariş zamanı", formatDateTime(order.createdAt)),
    ...detail("Toplam tutar", formatMoney(order.totalAmount)),
  ]);
  if (order.refundedAmount && order.refundedAmount !== "0.00") {
    details.append(...detail("İade edilen", formatMoney(order.refundedAmount)));
  }
  if (order.pickup) {
    details.append(...detail("Teslim alma aralığı", formatWindow(order.pickup.windowStart, order.pickup.windowEnd)));
    details.append(...detail("Teslim kodu", order.pickup.code, "pickup-code"));
    if (order.pickup.pickedUpAt) {
      details.append(...detail("Teslim alındı", formatDateTime(order.pickup.pickedUpAt)));
    }
  }
  if (order.cancelledAt) {
    details.append(...detail("İptal zamanı", formatDateTime(order.cancelledAt)));
  }
  const item = el("li", { className: "panel order", attrs: { id: "order-" + order.id } }, [
    el("h2", { className: "order-title", text: order.businessName + " · Sipariş #" + order.id }),
    details,
  ]);
  if (order.review) {
    const review = el("p", { className: "review", text: "Puanınız: " + order.review.rating + " / 5" });
    item.append(review);
    if (order.review.comment) {
      item.append(el("blockquote", { className: "review-comment", text: order.review.comment }));
    }
  }
  if (order.canCancel) {
    item.append(cancelButton(order, box));
  }
  if (order.canReview) {
    item.append(reviewForm(order, box));
  }
  item.append(box);
  return item;
}

function cancelButton(order, box) {
  return el("div", { className: "actions" }, [
    el("button", {
      className: "button button-danger",
      text: "Siparişi iptal et",
      attrs: { type: "button" },
      on: {
        click: async (event) => {
          const button = event.currentTarget;
          if (button.disabled) {
            return;
          }
          if (!window.confirm("Sipariş iptal edilip tutarın tamamı iade edilsin mi?")) {
            return;
          }
          button.disabled = true;
          hideMessage(box);
          try {
            const cancelled = await api.post("/orders/" + order.id + "/cancel", { requestId: cancelRequestId(order.id) });
            forgetCancelRequestId(order.id);
            replaceOrder(cancelled, "Siparişiniz iptal edildi, tutar iade edildi.");
          } catch (error) {
            // A server answer closes the attempt; after a lost connection the same request id is sent again.
            if (error instanceof ApiError && error.status !== 0) {
              forgetCancelRequestId(order.id);
            }
            button.disabled = false;
            showError(box, error);
          }
        },
      },
    }),
  ]);
}

function reviewForm(order, box) {
  const ratingId = "rating-" + order.id;
  const commentId = "comment-" + order.id;
  const rating = el("select", { attrs: { id: ratingId, name: "rating", required: true } }, [
    el("option", { text: "Puan seçin", attrs: { value: "" } }),
    ...[5, 4, 3, 2, 1].map((value) => el("option", { text: value + " / 5", attrs: { value: String(value) } })),
  ]);
  const comment = el("textarea", { attrs: { id: commentId, name: "comment", maxlength: "1000", rows: "3" } });
  const submit = el("button", { className: "button", text: "Değerlendir", attrs: { type: "submit" } });
  const form = el("form", { className: "form review-form", attrs: { novalidate: true } }, [
    el("h3", { text: "Siparişi değerlendirin" }),
    el("div", { className: "field" }, [el("label", { text: "Puan", attrs: { for: ratingId } }), rating]),
    el("div", { className: "field" }, [
      el("label", { text: "Yorum (isteğe bağlı, en fazla 1000 karakter)", attrs: { for: commentId } }),
      comment,
    ]),
    submit,
  ]);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submit.disabled) {
      return;
    }
    hideMessage(box);
    if (!rating.value) {
      showMessage(box, "error", "Lütfen 1 ile 5 arasında bir puan seçin.");
      return;
    }
    submit.disabled = true;
    try {
      await api.post("/orders/" + order.id + "/review", {
        rating: Number(rating.value),
        comment: comment.value.trim() || null,
      });
      replaceOrder(await api.get("/orders/" + order.id), "Değerlendirmeniz kaydedildi. Teşekkürler!");
    } catch (error) {
      submit.disabled = false;
      showError(box, error);
    }
  });
  return form;
}

/** Redraws one order with the answer of the server and shows a confirmation under it. */
function replaceOrder(order, confirmation) {
  const current = document.getElementById("order-" + order.id);
  const updated = orderItem(order);
  current.replaceWith(updated);
  const box = updated.querySelector(".message");
  showMessage(box, "success", confirmation);
}

async function load() {
  hideMessage(message);
  pager.hidden = true;
  loading(list);
  let result;
  try {
    result = await api.get("/orders", { status: statusSelect.value, page, size: PAGE_SIZE });
  } catch (error) {
    clear(list);
    showError(message, error);
    return;
  }
  clear(list);
  if (result.items.length === 0) {
    showMessage(message, "info", statusSelect.value ? "Bu durumda siparişiniz yok." : "Henüz siparişiniz yok.");
  }
  for (const order of result.items) {
    list.append(orderItem(order));
  }
  const pages = Math.max(1, Math.ceil(result.totalItems / result.size));
  pageInfo.textContent = "Sayfa " + (result.page + 1) + " / " + pages;
  previous.disabled = result.page === 0;
  next.disabled = result.page + 1 >= pages;
  pager.hidden = pages <= 1;
}

statusSelect.addEventListener("change", () => {
  page = 0;
  load();
});
previous.addEventListener("click", () => {
  page = Math.max(0, page - 1);
  load();
});
next.addEventListener("click", () => {
  page += 1;
  load();
});

if (await startPage()) {
  await load();
}
