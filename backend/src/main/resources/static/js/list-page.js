// A paginated list shown as a table, used by the administrator pages: loading, empty and error states, the pager,
// and the page-wide refusal when the first request is answered with 403.

import { clear, hideMessage, loading, showError, showMessage } from "./dom.js";
import { isAccessError, showAccessDenied } from "./layout.js";
import { renderPager, table } from "./table.js";

/**
 * Creates the list. fetch(page) reads one page; columns and rowId describe the table; empty() is the text of an empty
 * list. Returns { load, reload }: load(page) reads a page, reload() reads the current page again. load resolves to
 * false when the server refused the page.
 */
export function listPage({ list, message, pager, fetch, columns, rowId, empty }) {
  let page = 0;
  let loaded = false;

  async function load(nextPage = 0) {
    page = nextPage;
    hideMessage(message);
    loading(list);
    let result;
    try {
      result = await fetch(page);
    } catch (error) {
      clear(list);
      if (!loaded && isAccessError(error)) {
        showAccessDenied(error);
        return false;
      }
      showError(message, error);
      return true;
    }
    loaded = true;
    clear(list);
    if (result.items.length === 0) {
      showMessage(message, "info", empty());
    } else {
      list.append(table(columns, result.items, rowId));
    }
    renderPager(pager, result, load);
    return true;
  }

  return { load, reload: () => load(page) };
}

/** Shows the outcome of an action above the list. */
export function showOutcome(message, kind, text) {
  showMessage(message, kind, text);
  message.scrollIntoView({ block: "nearest" });
}
