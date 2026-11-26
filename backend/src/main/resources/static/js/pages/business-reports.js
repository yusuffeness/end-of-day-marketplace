// The sales report of a business for a date range (GET /businesses/{id}/reports): units sold, cancellations, net
// collection and the ratings.

import { api } from "../api.js";
import { businessPath, startBusinessPage } from "../business-context.js";
import { clear } from "../dom.js";
import { showPageError, startPage } from "../layout.js";
import { bindReportForm } from "../report-form.js";
import { reportView } from "../report-view.js";

const pickerSlot = document.getElementById("business-picker");
const content = document.getElementById("business-content");

async function start() {
  if (!(await startPage())) {
    return;
  }
  let context;
  try {
    context = await startBusinessPage(pickerSlot, content);
  } catch (error) {
    clear(content);
    showPageError(error.detail || "Beklenmeyen bir hata oluştu.");
    return;
  }
  if (!context) {
    return;
  }
  await bindReportForm({
    form: document.getElementById("report-form"),
    message: document.getElementById("report-message"),
    output: document.getElementById("report"),
    fetch: (from, to) => api.get(businessPath(context.id, "/reports"), { from, to }),
    render: (report) => reportView(report),
  });
}

await start();
