// The business that the business owner pages work on. The page address carries it (?id=3), so that a page of one
// business can be linked; without an id, the last chosen business of the browser tab or the first approved one is
// used. Whether the user may see the business is decided by the server on every request, not here.

import { api } from "./api.js";
import { el } from "./dom.js";
import { approvalStatusLabel } from "./format.js";

const LAST_BUSINESS_KEY = "eod.business";

function remembered() {
  try {
    return window.sessionStorage.getItem(LAST_BUSINESS_KEY);
  } catch {
    return null;
  }
}

function remember(id) {
  try {
    window.sessionStorage.setItem(LAST_BUSINESS_KEY, String(id));
  } catch {
    // The choice is only a convenience.
  }
}

/** The id in the address. Any value is passed on to the server, which answers an unknown one with 404. */
function idFromAddress() {
  const value = new URLSearchParams(window.location.search).get("id");
  return value ? value.trim() : null;
}

/**
 * Reads the user's businesses (GET /businesses/mine) and picks the one to show.
 *
 * Resolves to { businesses, id, business }: id is the business to request from the server (it may be one the user does
 * not own, when it was typed into the address), business is its entry in the user's list or null.
 */
export async function resolveBusiness() {
  const result = await api.get("/businesses/mine", { size: 100 });
  const businesses = result.items;
  const ownIds = businesses.map((business) => String(business.id));
  let id = idFromAddress();
  if (!id) {
    const last = remembered();
    const approved = businesses.find((business) => business.approvalStatus === "ONAYLANDI");
    id = last && ownIds.includes(last) ? last : approved ? String(approved.id) : ownIds[0] || null;
  }
  const business = businesses.find((candidate) => String(candidate.id) === id) || null;
  if (business) {
    remember(id);
  }
  return { businesses, id, business };
}

/** The path of a business owner endpoint, with the id from the address encoded. */
export function businessPath(id, rest = "") {
  return "/businesses/" + encodeURIComponent(id) + rest;
}

/** A selection of the user's businesses; choosing one opens the same page for it. */
export function businessPicker(businesses, currentId) {
  const select = el(
    "select",
    { attrs: { id: "business-select", name: "business" } },
    businesses.map((business) =>
      el("option", {
        text:
          business.name +
          " (" +
          business.district +
          ", " +
          business.city +
          ")" +
          (business.approvalStatus === "ONAYLANDI" ? "" : " · " + approvalStatusLabel(business.approvalStatus)),
        attrs: { value: String(business.id), selected: String(business.id) === String(currentId) },
      }),
    ),
  );
  select.addEventListener("change", () => {
    remember(select.value);
    window.location.assign(window.location.pathname + "?id=" + encodeURIComponent(select.value));
  });
  return el("div", { className: "field business-picker" }, [
    el("label", { text: "İşletme", attrs: { for: "business-select" } }),
    select,
  ]);
}

/** Shown when the user owns no business yet. */
export function noBusinessNotice() {
  return el("div", { className: "message message-info", attrs: { role: "status" } }, [
    el("p", { text: "Yetkilisi olduğunuz bir işletme yok." }),
    el("p", {}, [el("a", { text: "İşletme başvurusu yapın", attrs: { href: "/business-apply.html" } })]),
  ]);
}

/**
 * Prepares a business owner page: puts the business picker into pickerSlot and resolves to the id to work on, or to
 * null when the user owns no business (the notice is shown in place of content).
 */
export async function startBusinessPage(pickerSlot, content) {
  const context = await resolveBusiness();
  if (context.businesses.length > 0) {
    pickerSlot.replaceChildren(businessPicker(context.businesses, context.id));
  }
  if (!context.id) {
    content.replaceChildren(noBusinessNotice());
    return null;
  }
  return context;
}
