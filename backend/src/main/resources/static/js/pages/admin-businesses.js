// Activating and deactivating businesses (PUT /admin/businesses/{id}/active). The API has no separate business list
// for administrators, so the businesses are read by approval status through GET /admin/business-applications, whose
// items carry the active flag.

import { api } from "../api.js";
import { el, hideMessage, showError } from "../dom.js";
import { activeLabel, approvalStatusLabel, businessTypeLabel } from "../format.js";
import { startPage } from "../layout.js";
import { listPage, showOutcome } from "../list-page.js";
import { actionButton } from "../table.js";

const statusSelect = document.getElementById("status");
const outcome = document.getElementById("outcome");

function activeToggle(business) {
  const next = !business.active;
  return actionButton(next ? "Aktif yap" : "Pasif yap", {
    kind: next ? "secondary" : "danger",
    question: next
      ? "\"" + business.name + "\" aktif yapılsın mı?"
      : "\"" + business.name + "\" pasif yapılsın mı? İlanları aramadan kalkar ve yeni sipariş almaz.",
    onClick: async () => {
      hideMessage(outcome);
      try {
        const updated = await api.put("/admin/businesses/" + business.id + "/active", { active: next });
        showOutcome(outcome, "success", "\"" + updated.name + "\" " + (updated.active ? "aktif" : "pasif") + " yapıldı.");
        await list.reload();
      } catch (error) {
        showError(outcome, error);
      }
    },
  });
}

const list = listPage({
  list: document.getElementById("list"),
  message: document.getElementById("list-message"),
  pager: document.getElementById("pager"),
  fetch: (page) => api.get("/admin/business-applications", { status: statusSelect.value, page, size: 20 }),
  rowId: (business) => "business-" + business.id,
  empty: () => "Bu durumda işletme yok.",
  columns: [
    { label: "İşletme", value: (business) => business.name },
    { label: "Tür", value: (business) => businessTypeLabel(business.type) },
    { label: "Konum", value: (business) => business.district + ", " + business.city },
    { label: "Yetkili", value: (business) => business.ownerFirstName + " " + business.ownerLastName },
    { label: "Onay durumu", value: (business) => approvalStatusLabel(business.approvalStatus) },
    {
      label: "Durum",
      value: (business) =>
        el("span", {
          className: business.active ? "status-active" : "status-inactive",
          text: activeLabel(business.active),
        }),
    },
    { label: "İşlemler", value: activeToggle },
  ],
});

statusSelect.addEventListener("change", () => {
  hideMessage(outcome);
  list.load(0);
});

if (await startPage()) {
  await list.load(0);
}
