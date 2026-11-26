// Listing details (GET /listings/{id}) and adding the package to the cart. The cart holds the packages of one business;
// a package of another business replaces the cart only after the user agrees.

import { api } from "../api.js";
import { clearFieldErrors, el, hideMessage, loading, setFieldError, showError, showMessage } from "../dom.js";
import { businessTypeLabel, formatMoney, formatWindow } from "../format.js";
import { addToCart, isOtherBusiness, loadCart, quantityOf } from "../cart.js";
import { startPage, updateCartCounter } from "../layout.js";

const message = document.getElementById("listing-message");
const article = document.getElementById("listing");
const form = document.getElementById("add-form");
const addMessage = document.getElementById("add-message");
const quantityInput = document.getElementById("quantity");

const REPLACE_QUESTION =
  "Sepetinizde başka bir işletmenin paketleri var. Bir siparişte yalnızca tek işletmenin paketleri olabilir.\n\n" +
  "Sepet boşaltılıp yeni işletmeden devam edilsin mi?";

let listing = null;

function text(id, value) {
  document.getElementById(id).textContent = value;
}

function render() {
  text("listing-title", listing.title);
  document.title = listing.title + " · Gün Sonu";
  text("listing-business", listing.businessName + " · " + businessTypeLabel(listing.businessType));
  text("listing-sale-price", formatMoney(listing.salePrice));
  text("listing-normal-price", formatMoney(listing.normalPrice));
  text("listing-description", listing.description);
  text("listing-allergens", listing.allergenInfo);
  text("listing-remaining", listing.remainingQuantity + " adet");
  text("listing-window", formatWindow(listing.pickupStart, listing.pickupEnd));
  text("listing-address", listing.address + ", " + listing.district + ", " + listing.city);
  text("listing-phone", listing.phone);
  const available = listing.status === "AKTIF" && listing.remainingQuantity > 0;
  form.hidden = !available;
  quantityInput.max = String(listing.remainingQuantity);
  if (!available) {
    showMessage(addMessage, "info", "Bu paket şu an satışta değil.");
  }
  article.hidden = false;
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  clearFieldErrors(form);
  hideMessage(addMessage);
  const quantity = Number(quantityInput.value);
  if (!Number.isInteger(quantity) || quantity < 1) {
    setFieldError(form, "quantity", "Adet en az 1 olmalıdır.");
    return;
  }
  let cart = loadCart();
  let replace = false;
  if (isOtherBusiness(cart, listing)) {
    if (!window.confirm(REPLACE_QUESTION)) {
      return;
    }
    replace = true;
    cart = { items: [] };
  }
  const inCart = replace ? 0 : quantityOf(cart, listing.id);
  if (!addToCart(listing, quantity, { replace })) {
    const left = listing.remainingQuantity - inCart;
    setFieldError(
      form,
      "quantity",
      left > 0
        ? "Bu paketten en fazla " + left + " adet daha ekleyebilirsiniz."
        : "Bu paketin kalan adetlerinin hepsi zaten sepetinizde.",
    );
    return;
  }
  updateCartCounter();
  showMessage(addMessage, "success", quantity + " adet sepete eklendi.");
  addMessage.append(el("p", {}, [el("a", { text: "Sepete git", attrs: { href: "/cart.html" } })]));
});

async function load() {
  const id = new URLSearchParams(window.location.search).get("id");
  if (!id || !/^\d+$/.test(id)) {
    showMessage(message, "error", "Paket bulunamadı.");
    return;
  }
  loading(message);
  message.hidden = false;
  try {
    listing = await api.get("/listings/" + id);
  } catch (error) {
    showError(message, error);
    return;
  }
  hideMessage(message);
  render();
}

if (await startPage()) {
  await load();
}
