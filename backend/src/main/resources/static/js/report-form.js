// The date range form of the two report pages. The range is checked before sending (both dates, start not after
// the end); the server checks it again, including the 366-day limit.

import { clear, hideMessage, loading, showError, showMessage } from "./dom.js";
import { monthStartIso, todayIso } from "./format.js";
import { isAccessError, showAccessDenied } from "./layout.js";

/**
 * Binds the form: fetch(from, to) reads the report, render(report) returns its nodes. The first report (this month)
 * is loaded at once; a refusal of the server replaces the page with the access message.
 */
export async function bindReportForm({ form, message, output, fetch, render }) {
  const fromInput = form.elements.namedItem("from");
  const toInput = form.elements.namedItem("to");
  const button = form.querySelector('button[type="submit"]');
  fromInput.value = monthStartIso();
  toInput.value = todayIso();

  async function load(first) {
    hideMessage(message);
    const from = fromInput.value;
    const to = toInput.value;
    if (!from || !to) {
      showMessage(message, "error", "Başlangıç ve bitiş tarihlerini seçin.");
      return;
    }
    if (from > to) {
      showMessage(message, "error", "Başlangıç tarihi bitiş tarihinden sonra olamaz.");
      return;
    }
    button.disabled = true;
    loading(output);
    try {
      const report = await fetch(from, to);
      output.replaceChildren(...render(report));
    } catch (error) {
      clear(output);
      if (first && isAccessError(error)) {
        showAccessDenied(error);
        return;
      }
      showError(message, error);
    } finally {
      button.disabled = false;
    }
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    load(false);
  });
  await load(true);
}
