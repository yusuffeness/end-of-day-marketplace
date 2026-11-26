// Plain tables, the pager of paginated lists and buttons that ask before they act. Cell values are set as text; a
// cell may also be a ready node, such as a button.

import { clear, el } from "./dom.js";

/**
 * Builds a table.
 *
 * @param {Array<{label: string, value: function(object): (Node|string|number|null), className?: string}>} columns
 * @param {Array<object>} rows
 * @param {function(object): string} [rowId] id attribute of a row
 */
export function table(columns, rows, rowId) {
  const head = el("tr", {}, columns.map((column) => el("th", { text: column.label, attrs: { scope: "col" } })));
  const body = el("tbody");
  for (const row of rows) {
    const cells = columns.map((column) => {
      const value = column.value(row);
      const cell = el("td", { className: column.className, attrs: { "data-label": column.label } });
      if (value instanceof Node) {
        cell.append(value);
      } else if (value !== null && value !== undefined) {
        cell.textContent = String(value);
      }
      return cell;
    });
    body.append(el("tr", { attrs: { id: rowId ? rowId(row) : null } }, cells));
  }
  return el("div", { className: "table-wrap" }, [el("table", { className: "table" }, [el("thead", {}, [head]), body])]);
}

/** Draws the pager of a paginated answer into nav; onPage is called with the zero-based page to load. */
export function renderPager(nav, result, onPage) {
  clear(nav);
  const pages = Math.max(1, Math.ceil(result.totalItems / result.size));
  nav.hidden = pages <= 1;
  if (pages <= 1) {
    return;
  }
  const button = (text, page, disabled) =>
    el("button", {
      className: "button button-secondary",
      text,
      attrs: { type: "button", disabled },
      on: { click: () => onPage(page) },
    });
  nav.append(
    button("Önceki", result.page - 1, result.page === 0),
    el("span", { text: "Sayfa " + (result.page + 1) + " / " + pages + " · " + result.totalItems + " kayıt" }),
    button("Sonraki", result.page + 1, result.page + 1 >= pages),
  );
}

/**
 * A button that asks the question first (when given) and stays disabled while onClick runs, so that one click sends
 * one request.
 */
export function actionButton(text, { question, kind = "secondary", className, label, onClick }) {
  return el("button", {
    className: className || "button button-" + kind + " button-small",
    text,
    attrs: { type: "button", "aria-label": label },
    on: {
      click: async (event) => {
        const button = event.currentTarget;
        if (button.disabled || (question && !window.confirm(question))) {
          return;
        }
        button.disabled = true;
        try {
          await onClick();
        } finally {
          button.disabled = false;
        }
      },
    },
  });
}
