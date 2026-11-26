// Client-side checks of the account forms. They only catch obvious mistakes before a request; the server checks every
// rule again and its field errors are shown in the same places.

import { setFieldError } from "./dom.js";

/** The same shape as the server's rule: something, an @, a domain with a dot. */
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const PHONE = /^[0-9+() -]{7,20}$/;

export function requireValue(form, field, message) {
  const value = form.elements.namedItem(field).value.trim();
  if (!value) {
    setFieldError(form, field, message);
    return false;
  }
  return true;
}

export function requireEmail(form, field) {
  const value = form.elements.namedItem(field).value.trim();
  if (!value) {
    setFieldError(form, field, "E-posta adresi zorunludur.");
    return false;
  }
  if (!EMAIL.test(value)) {
    setFieldError(form, field, "Geçerli bir e-posta adresi giriniz.");
    return false;
  }
  return true;
}

export function optionalPhone(form, field) {
  const value = form.elements.namedItem(field).value.trim();
  if (value && !PHONE.test(value)) {
    setFieldError(form, field, "Geçerli bir telefon numarası giriniz.");
    return false;
  }
  return true;
}

/** Length in characters as the server counts it (code points), with at least one letter and one digit. */
export function newPassword(form, field) {
  const value = form.elements.namedItem(field).value;
  const length = Array.from(value).length;
  if (length < 8 || length > 72) {
    setFieldError(form, field, "Parola 8 ile 72 karakter arasında olmalıdır.");
    return false;
  }
  if (!/\p{L}/u.test(value) || !/\p{N}/u.test(value)) {
    setFieldError(form, field, "Parola en az bir harf ve bir rakam içermelidir.");
    return false;
  }
  return true;
}

export function sameValue(form, field, other, message) {
  if (form.elements.namedItem(field).value !== form.elements.namedItem(other).value) {
    setFieldError(form, field, message);
    return false;
  }
  return true;
}

/** Disables the submit button while a request runs, so that one click sends one request. */
export async function whileSubmitting(form, action) {
  const button = form.querySelector('button[type="submit"]');
  if (button.disabled) {
    return;
  }
  button.disabled = true;
  try {
    await action();
  } finally {
    button.disabled = false;
  }
}

const AMOUNT = /^\d{1,8}(?:[.,]\d{1,2})?$/;

/**
 * Reads an amount field ("60", "60,5" or "60.50") in the API format with two decimals ("60.50"). Sets a field error
 * and returns null when the value is not an amount with up to 8 integer digits and 2 decimals.
 */
export function amountValue(form, field, label) {
  const value = form.elements.namedItem(field).value.trim();
  if (!value) {
    setFieldError(form, field, label + " zorunludur.");
    return null;
  }
  if (!AMOUNT.test(value)) {
    setFieldError(form, field, label + " geçerli bir tutar olmalıdır (en fazla 2 ondalık).");
    return null;
  }
  const [whole, fraction = ""] = value.replace(",", ".").split(".");
  return String(Number(whole)) + "." + fraction.padEnd(2, "0");
}
