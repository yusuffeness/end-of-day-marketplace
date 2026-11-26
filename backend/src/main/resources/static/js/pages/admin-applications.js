// Business applications for the administrator (GET /admin/business-applications), oldest first, with approval
// (POST /admin/businesses/{id}/approve) and rejection (POST /admin/businesses/{id}/reject) of pending ones. Approval
// grants the owner the business owner role in the same transaction.

import { api } from "../api.js";
import { el, hideMessage, showError } from "../dom.js";
import { approvalStatusLabel, businessTypeLabel } from "../format.js";
import { startPage } from "../layout.js";
import { listPage, showOutcome } from "../list-page.js";
import { actionButton } from "../table.js";

const statusSelect = document.getElementById("status");
const outcome = document.getElementById("outcome");

function decide(application, action, verb) {
  return async () => {
    hideMessage(outcome);
    try {
      const business = await api.post("/admin/businesses/" + application.id + "/" + action);
      showOutcome(outcome, "success", "\"" + business.name + "\" başvurusu " + verb + ".");
      await list.reload();
    } catch (error) {
      showError(outcome, error);
    }
  };
}

function actions(application) {
  if (application.approvalStatus !== "BEKLEMEDE") {
    return null;
  }
  const owner = application.ownerFirstName + " " + application.ownerLastName;
  return el("div", { className: "cell-actions" }, [
    actionButton("Onayla", {
      kind: "primary",
      question: "\"" + application.name + "\" başvurusu onaylansın mı? " + owner + " işletme yetkilisi rolünü alacak.",
      onClick: decide(application, "approve", "onaylandı"),
    }),
    actionButton("Reddet", {
      kind: "danger",
      question: "\"" + application.name + "\" başvurusu reddedilsin mi?",
      onClick: decide(application, "reject", "reddedildi"),
    }),
  ]);
}

const list = listPage({
  list: document.getElementById("list"),
  message: document.getElementById("list-message"),
  pager: document.getElementById("pager"),
  fetch: (page) => api.get("/admin/business-applications", { status: statusSelect.value, page, size: 20 }),
  rowId: (application) => "application-" + application.id,
  empty: () => "Bu durumda başvuru yok.",
  columns: [
    { label: "İşletme", value: (application) => application.name },
    { label: "Tür", value: (application) => businessTypeLabel(application.type) },
    { label: "Konum", value: (application) => application.district + ", " + application.city },
    { label: "Adres", value: (application) => application.address },
    { label: "Telefon", value: (application) => application.phone },
    { label: "Başvuran", value: (application) => application.ownerFirstName + " " + application.ownerLastName },
    {
      label: "Onay durumu",
      value: (application) =>
        el("span", {
          className: "approval-" + application.approvalStatus,
          text: approvalStatusLabel(application.approvalStatus),
        }),
    },
    { label: "İşlemler", value: actions },
  ],
});

statusSelect.addEventListener("change", () => {
  hideMessage(outcome);
  list.load(0);
});

if (await startPage()) {
  await list.load(0);
}
