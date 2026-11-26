// The cart of the browser tab, kept in sessionStorage. It holds packages of one business only (an order belongs to one
// business). Prices and remaining units are copies for display; the server reads both from the database again.
//
// The request id of the next order attempt is kept with the cart: sending the same attempt again reuses it, so the
// server creates at most one order. Any change of the cart starts a new attempt.

import { toKurus } from "./format.js";

const KEY = "eod.cart";

function empty() {
  return { businessId: null, businessName: null, items: [], requestId: null };
}

export function loadCart() {
  try {
    const stored = window.sessionStorage.getItem(KEY);
    const cart = stored ? JSON.parse(stored) : null;
    if (cart && Array.isArray(cart.items)) {
      return cart;
    }
  } catch {
    // Unreadable or blocked storage: start with an empty cart.
  }
  return empty();
}

function saveCart(cart) {
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(cart));
  } catch {
    // Storage may be full or blocked; the cart then lives only as long as the page.
  }
}

export function clearCart() {
  try {
    window.sessionStorage.removeItem(KEY);
  } catch {
    // Nothing to clear when the storage is blocked.
  }
}

/** True when the listing belongs to another business than the packages already in the cart. */
export function isOtherBusiness(cart, listing) {
  return cart.items.length > 0 && cart.businessId !== listing.businessId;
}

export function quantityOf(cart, listingId) {
  const item = cart.items.find((entry) => entry.listingId === listingId);
  return item ? item.quantity : 0;
}

/**
 * Adds units of a listing. The caller has asked the user before replacing a cart of another business.
 *
 * @returns {boolean} false when the cart would then hold more units than the listing has left
 */
export function addToCart(listing, quantity, { replace = false } = {}) {
  let cart = loadCart();
  if (replace || cart.items.length === 0) {
    cart = empty();
    cart.businessId = listing.businessId;
    cart.businessName = listing.businessName;
  }
  const existing = cart.items.find((entry) => entry.listingId === listing.id);
  const total = (existing ? existing.quantity : 0) + quantity;
  if (total > listing.remainingQuantity) {
    return false;
  }
  if (existing) {
    existing.quantity = total;
    refresh(existing, listing);
  } else {
    const item = { listingId: listing.id, quantity: total };
    refresh(item, listing);
    cart.items.push(item);
  }
  cart.requestId = null;
  saveCart(cart);
  return true;
}

function refresh(item, listing) {
  item.title = listing.title;
  item.salePrice = listing.salePrice;
  item.remainingQuantity = listing.remainingQuantity;
  item.pickupStart = listing.pickupStart;
  item.pickupEnd = listing.pickupEnd;
}

/** Updates the copies of price and remaining units after the cart page read the listings again. */
export function refreshItem(listing) {
  const cart = loadCart();
  const item = cart.items.find((entry) => entry.listingId === listing.id);
  if (item) {
    refresh(item, listing);
    saveCart(cart);
  }
  return cart;
}

export function setQuantity(listingId, quantity) {
  const cart = loadCart();
  const item = cart.items.find((entry) => entry.listingId === listingId);
  if (item) {
    item.quantity = quantity;
    cart.requestId = null;
    saveCart(cart);
  }
  return cart;
}

export function removeFromCart(listingId) {
  const cart = loadCart();
  cart.items = cart.items.filter((entry) => entry.listingId !== listingId);
  cart.requestId = null;
  if (cart.items.length === 0) {
    clearCart();
    return empty();
  }
  saveCart(cart);
  return cart;
}

/** The request id of the current order attempt, created once with crypto.randomUUID. */
export function orderRequestId() {
  const cart = loadCart();
  if (!cart.requestId) {
    cart.requestId = window.crypto.randomUUID();
    saveCart(cart);
  }
  return cart.requestId;
}

/** Forgets the request id after the server answered with an error, so that the next attempt is a new request. */
export function resetOrderRequestId() {
  const cart = loadCart();
  cart.requestId = null;
  saveCart(cart);
}

export function totalKurus(cart) {
  return cart.items.reduce((sum, item) => sum + toKurus(item.salePrice) * item.quantity, 0);
}

export function itemCount(cart) {
  return cart.items.reduce((sum, item) => sum + item.quantity, 0);
}
