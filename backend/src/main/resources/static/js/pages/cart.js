// The cart and the order (POST /orders). Before showing the cart, every listing is read again (GET /listings/{id}) so
// that the price and the units left are current. The server decides about stock and prices when the order is placed.
//
// One click sends one order: the button is locked until the answer arrives, and the request id of the attempt is kept
// with the cart, so a repeated attempt (for example after a lost connection) cannot create a second order.

import { api, ApiError } from "../api.js";
import { clear, el, hideMessage, loading, showError, showMessage } from "../dom.js";
import { formatKurus, formatMoney, formatWindow } from "../format.js";
import {
  clearCart,
  loadCart,
  orderRequestId,
  refreshItem,
  removeFromCart,
  resetOrderRequestId,
  setQuantity,
  totalKurus,
} from "../cart.js";
import { startPage, updateCartCounter } from "../layout.js";

const message = document.getElementById("cart-message");
const section = document.getElementById("cart");
const list = document.getElementById("cart-items");
const total = document.getElementById("cart-total");
const placeButton = document.getElementById("place-order");
const emptyButton = document.getElementById("empty-cart");
const result = document.getElementById("order-result");

let placing = false;
/** Listings that could not be read again, by id: they cannot be ordered. */
const unavailable = new Set();

function itemRow(item) {
  const quantity = el("input", {
    attrs: {
      type: "number",
      min: "1",
      max: String(item.remainingQuantity),
      step: "1",
      value: String(item.quantity),
      id: "quantity-" + item.listingId,
      disabled: unavailable.has(item.listingId) || placing,
    },
    on: { change: (event) => changeQuantity(item, event.currentTarget) },
  });
  const row = el("li", { className: "row" }, [
    el("div", { className: "row-main" }, [
      el("a", { text: item.title, attrs: { href: "/listing.html?id=" + encodeURIComponent(item.listingId) } }),
      el("p", { className: "muted", text: "Teslim alma: " + formatWindow(item.pickupStart, item.pickupEnd) }),
      el("p", { className: "muted", text: "Birim fiyat: " + formatMoney(item.salePrice) + " · Kalan: " + item.remainingQuantity + " adet" }),
    ]),
    el("div", { className: "row-side" }, [
      el("label", { text: "Adet", attrs: { for: "quantity-" + item.listingId } }),
      quantity,
      el("button", {
        className: "button button-secondary",
        text: "Çıkar",
        attrs: { type: "button", disabled: placing },
        on: { click: () => remove(item) },
      }),
    ]),
  ]);
  if (unavailable.has(item.listingId)) {
    row.append(el("p", { className: "field-error", text: "Bu paket artık satışta değil; sepetten çıkarın." }));
  }
  return row;
}

function render() {
  const cart = loadCart();
  clear(list);
  if (cart.items.length === 0) {
    section.hidden = true;
    if (result.hidden) {
      showMessage(message, "info", "Sepetiniz boş.");
      message.append(el("p", {}, [el("a", { text: "Paketlere göz atın", attrs: { href: "/index.html" } })]));
    }
    return;
  }
  section.hidden = false;
  document.getElementById("cart-business").textContent = "İşletme: " + cart.businessName;
  for (const item of cart.items) {
    list.append(itemRow(item));
  }
  total.textContent = formatKurus(totalKurus(cart));
  placeButton.disabled = placing || cart.items.some((item) => unavailable.has(item.listingId));
  emptyButton.disabled = placing;
  updateCartCounter();
}

function changeQuantity(item, input) {
  const quantity = Number(input.value);
  if (!Number.isInteger(quantity) || quantity < 1) {
    input.value = String(item.quantity);
    showMessage(message, "error", "Adet en az 1 olmalıdır.");
    return;
  }
  if (quantity > item.remainingQuantity) {
    input.value = String(item.quantity);
    showMessage(message, "error", "Bu paketten en fazla " + item.remainingQuantity + " adet alabilirsiniz.");
    return;
  }
  hideMessage(message);
  setQuantity(item.listingId, quantity);
  render();
}

function remove(item) {
  removeFromCart(item.listingId);
  unavailable.delete(item.listingId);
  hideMessage(message);
  render();
}

emptyButton.addEventListener("click", () => {
  if (window.confirm("Sepetteki bütün paketler çıkarılsın mı?")) {
    clearCart();
    unavailable.clear();
    hideMessage(message);
    render();
  }
});

placeButton.addEventListener("click", async () => {
  // The flag and the disabled button together make a second click do nothing until the answer arrives.
  if (placing) {
    return;
  }
  placing = true;
  placeButton.disabled = true;
  placeButton.textContent = "Sipariş gönderiliyor...";
  hideMessage(message);
  const cart = loadCart();
  const body = {
    businessId: cart.businessId,
    items: cart.items.map((item) => ({ listingId: item.listingId, quantity: item.quantity })),
    requestId: orderRequestId(),
  };
  try {
    const order = await api.post("/orders", body);
    clearCart();
    showResult(order);
  } catch (error) {
    // The server stored nothing for a rejected order, so the next attempt gets a new request id. After a lost
    // connection the outcome is unknown, so the same id is sent again and returns the first order if there is one.
    if (error instanceof ApiError && error.status !== 0) {
      resetOrderRequestId();
    }
    showError(message, error);
  } finally {
    placing = false;
    placeButton.textContent = "Siparişi onayla ve öde";
    render();
  }
});

function showResult(order) {
  document.getElementById("result-code").textContent = order.pickup ? order.pickup.code : "";
  document.getElementById("result-window").textContent = order.pickup
    ? formatWindow(order.pickup.windowStart, order.pickup.windowEnd)
    : "";
  document.getElementById("result-business").textContent = order.businessName;
  document.getElementById("result-total").textContent = formatMoney(order.totalAmount);
  result.hidden = false;
  hideMessage(message);
}

async function refreshListings() {
  const cart = loadCart();
  if (cart.items.length === 0) {
    return;
  }
  loading(list);
  section.hidden = false;
  await Promise.all(
    cart.items.map(async (item) => {
      try {
        const listing = await api.get("/listings/" + item.listingId);
        if (listing.status !== "AKTIF" || listing.remainingQuantity === 0) {
          unavailable.add(item.listingId);
        }
        refreshItem(listing);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          unavailable.add(item.listingId);
        } else {
          throw error;
        }
      }
    }),
  );
}

if (await startPage()) {
  try {
    await refreshListings();
  } catch (error) {
    showError(message, error);
  }
  render();
}
