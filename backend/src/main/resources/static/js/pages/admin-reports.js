// The platform report for a date range (GET /admin/reports/platform), read by the server from the reporting views
// through the read-only eod_report account.

import { api } from "../api.js";
import { startPage } from "../layout.js";
import { bindReportForm } from "../report-form.js";
import { reportView } from "../report-view.js";

if (await startPage()) {
  await bindReportForm({
    form: document.getElementById("report-form"),
    message: document.getElementById("report-message"),
    output: document.getElementById("report"),
    fetch: (from, to) => api.get("/admin/reports/platform", { from, to }),
    render: (report) =>
      reportView(
        report,
        [
          ["Aktif işletme", String(report.activeBusinessCount)],
          ["Yeni kullanıcı", String(report.newUserCount)],
        ],
        [
          ["Onaylı ve aktif işletme (şu an)", String(report.activeBusinessCount)],
          ["Aralıkta kayıt olan kullanıcı", String(report.newUserCount)],
        ],
      ),
  });
}
