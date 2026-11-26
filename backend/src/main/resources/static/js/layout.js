// The top bar of every page: brand, the menu of the signed-in user's roles and the sign-out button.

import { api, ApiError, MESSAGES } from "./api.js";
import { clearCart, itemCount, loadCart } from "./cart.js";
import { el } from "./dom.js";
import { menuFor } from "./menu.js";

/**
 * Prepares a page that needs a signed-in user: reads GET /me (a missing session sends the user to the sign-in page)
 * and draws the top bar. Resolves to the user, or to null when the page is being left.
 */
export async function startPage() {
  const header = document.getElementById("site-header");
  let user;
  try {
    user = await api.get("/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    renderHeader(header, null);
    showPageError(error instanceof ApiError ? error.detail : MESSAGES.unexpected);
    return null;
  }
  renderHeader(header, user);
  return user;
}

function renderHeader(header, user) {
  header.replaceChildren();
  const bar = el("div", { className: "topbar" }, [
    el("a", { className: "brand", text: "Gün Sonu", attrs: { href: "/index.html" } }),
  ]);
  if (user) {
    const nav = el("nav", { className: "menu", attrs: { "aria-label": "Ana menü" } });
    for (const section of menuFor(user.roles)) {
      const list = el("ul");
      for (const item of section.items) {
        const current = window.location.pathname === item.href;
        list.append(el("li", {}, [
          el("a", {
            text: item.href === "/cart.html" ? cartLabel(item.label) : item.label,
            attrs: { href: item.href, "aria-current": current ? "page" : null },
          }),
        ]));
      }
      nav.append(el("div", { className: "menu-section" }, [
        el("span", { className: "menu-title", text: section.section }),
        list,
      ]));
    }
    const account = el("div", { className: "account" }, [
      el("span", { className: "account-name", text: user.firstName + " " + user.lastName }),
      el("button", { className: "button button-secondary", text: "Çıkış yap", attrs: { type: "button" }, on: { click: signOut } }),
    ]);
    bar.append(nav, account);
  }
  header.append(bar);
}

function cartLabel(label) {
  const count = itemCount(loadCart());
  return count > 0 ? label + " (" + count + ")" : label;
}

/** Redraws the cart counter of the menu after the cart changed. */
export function updateCartCounter() {
  const link = document.querySelector('.menu a[href="/cart.html"]');
  if (link) {
    link.textContent = cartLabel("Sepet");
  }
}

async function signOut(event) {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    await api.post("/auth/logout");
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) {
      button.disabled = false;
      showPageError(error instanceof ApiError ? error.detail : MESSAGES.unexpected);
      return;
    }
  }
  // The cart belongs to this sign-in; the next user of the browser tab starts with an empty one.
  clearCart();
  window.location.assign("/login.html");
}

/** Shows an error that concerns the whole page, above its content. */
export function showPageError(text) {
  const main = document.getElementById("main");
  if (!main) {
    return;
  }
  let box = document.getElementById("page-error");
  if (!box) {
    box = el("div", { className: "message message-error", attrs: { id: "page-error", role: "alert" } });
    main.prepend(box);
  }
  box.replaceChildren(el("p", { text }));
}

export const ACCESS_DENIED = "Bu sayfaya erişim yetkiniz yok.";

/** Whether the server refused the page itself: no right (403) or nothing behind the id in the address (404). */
export function isAccessError(error) {
  return error instanceof ApiError && (error.status === 403 || error.status === 404);
}

/**
 * Replaces the content of the page with the server's refusal; only the page title stays. The menu only hides links,
 * so a page opened by its address relies on this answer of the server.
 */
export function showAccessDenied(error) {
  const main = document.getElementById("main");
  if (!main) {
    return;
  }
  const title = main.querySelector("h1");
  const text = error.status === 404 ? "İstenen kayıt bulunamadı veya bu sayfaya erişim yetkiniz yok." : ACCESS_DENIED;
  const box = el("div", { className: "message message-error", attrs: { id: "page-error", role: "alert" } }, [
    el("p", { text }),
  ]);
  main.replaceChildren(...(title ? [title] : []), box);
}
