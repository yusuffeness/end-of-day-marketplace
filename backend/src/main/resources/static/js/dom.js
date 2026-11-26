// Small DOM helpers. Text from users or from the API is always set with textContent; innerHTML is never used with
// data, so markup in a listing title or a comment is shown as plain text.

/**
 * Creates an element.
 *
 * @param {string} tag element name
 * @param {object} [options] className, text (set as textContent), attrs (attribute values), on (event listeners)
 * @param {Array<Node|string|null>} [children] child nodes; strings become text nodes
 */
export function el(tag, options = {}, children = []) {
  const node = document.createElement(tag);
  if (options.className) {
    node.className = options.className;
  }
  if (options.text !== undefined && options.text !== null) {
    node.textContent = String(options.text);
  }
  for (const [name, value] of Object.entries(options.attrs || {})) {
    if (value === true) {
      node.setAttribute(name, "");
    } else if (value !== false && value !== null && value !== undefined) {
      node.setAttribute(name, String(value));
    }
  }
  for (const [event, listener] of Object.entries(options.on || {})) {
    node.addEventListener(event, listener);
  }
  for (const child of children) {
    if (child === null || child === undefined) {
      continue;
    }
    node.append(typeof child === "string" ? document.createTextNode(child) : child);
  }
  return node;
}

export function clear(node) {
  node.replaceChildren();
}

/**
 * Shows a message in a container: kind is "error", "success" or "info". The EOD code of an error is shown in a small
 * detail line.
 */
export function showMessage(container, kind, text, code) {
  clear(container);
  container.hidden = false;
  container.className = "message message-" + kind;
  container.setAttribute("role", kind === "error" ? "alert" : "status");
  container.append(el("p", { text }));
  if (code) {
    container.append(el("p", { className: "message-code", text: "Hata kodu: " + code }));
  }
}

export function hideMessage(container) {
  clear(container);
  container.hidden = true;
}

/** Shows an ApiError, or any other error with a generic text. */
export function showError(container, error) {
  if (error && error.detail) {
    showMessage(container, "error", error.detail, error.code);
  } else {
    showMessage(container, "error", "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.");
    console.error(error);
  }
}

/** Shows the field errors of an EOD-400 answer next to the inputs marked with data-error-for. */
export function showFieldErrors(form, fieldErrors) {
  clearFieldErrors(form);
  for (const error of fieldErrors || []) {
    const slot = form.querySelector('[data-error-for="' + CSS.escape(error.field) + '"]');
    if (slot) {
      slot.textContent = slot.textContent ? slot.textContent + " " + error.message : error.message;
      const input = form.elements.namedItem(error.field);
      if (input && input.setAttribute) {
        input.setAttribute("aria-invalid", "true");
      }
    }
  }
}

export function clearFieldErrors(form) {
  for (const slot of form.querySelectorAll("[data-error-for]")) {
    slot.textContent = "";
  }
  for (const input of form.querySelectorAll("[aria-invalid]")) {
    input.removeAttribute("aria-invalid");
  }
}

/** Sets a client-side validation message for one field. */
export function setFieldError(form, field, message) {
  const slot = form.querySelector('[data-error-for="' + CSS.escape(field) + '"]');
  if (slot) {
    slot.textContent = message;
  }
  const input = form.elements.namedItem(field);
  if (input && input.setAttribute) {
    input.setAttribute("aria-invalid", "true");
  }
}

/** The "Yükleniyor..." placeholder of a list or a section. */
export function loading(container, text = "Yükleniyor...") {
  clear(container);
  container.append(el("p", { className: "loading", text, attrs: { role: "status" } }));
}
