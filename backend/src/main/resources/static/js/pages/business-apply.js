// The business application (POST /businesses) and the user's applications with their approval status
// (GET /businesses/mine). Every signed-in user may apply; the business owner role comes with the approval.

import { api, ApiError } from "../api.js";
import { clear, clearFieldErrors, el, hideMessage, loading, setFieldError, showError, showFieldErrors, showMessage } from "../dom.js";
import { activeLabel, approvalStatusLabel, businessTypeLabel } from "../format.js";
import { requireValue, whileSubmitting } from "../form.js";
import { startPage } from "../layout.js";
import { table } from "../table.js";

const PHONE = /^[0-9+() -]{7,20}$/;

const form = document.getElementById("apply-form");
const formMessage = document.getElementById("apply-message");
const list = document.getElementById("applications");
const listMessage = document.getElementById("applications-message");

function manageLink(business) {
  if (business.approvalStatus !== "ONAYLANDI") {
    return null;
  }
  return el("a", { text: "Yönet", attrs: { href: "/business.html?id=" + encodeURIComponent(business.id) } });
}

async function loadApplications() {
  hideMessage(listMessage);
  loading(list);
  let result;
  try {
    result = await api.get("/businesses/mine", { size: 100 });
  } catch (error) {
    clear(list);
    showError(listMessage, error);
    return;
  }
  clear(list);
  if (result.items.length === 0) {
    showMessage(listMessage, "info", "Henüz başvurunuz yok.");
    return;
  }
  list.append(
    table(
      [
        { label: "İşletme", value: (business) => business.name },
        { label: "Tür", value: (business) => businessTypeLabel(business.type) },
        { label: "Konum", value: (business) => business.district + ", " + business.city },
        {
          label: "Onay durumu",
          value: (business) =>
            el("span", {
              className: "approval-" + business.approvalStatus,
              text: approvalStatusLabel(business.approvalStatus),
            }),
        },
        { label: "Durum", value: (business) => activeLabel(business.active) },
        { label: "", value: manageLink },
      ],
      result.items,
      (business) => "business-" + business.id,
    ),
  );
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  clearFieldErrors(form);
  hideMessage(formMessage);
  const field = (name) => form.elements.namedItem(name).value.trim();
  let valid = [
    requireValue(form, "name", "İşletme adı zorunludur."),
    requireValue(form, "type", "İşletme türü seçin."),
    requireValue(form, "city", "Şehir zorunludur."),
    requireValue(form, "district", "İlçe zorunludur."),
    requireValue(form, "address", "Adres zorunludur."),
    requireValue(form, "phone", "Telefon zorunludur."),
  ].every(Boolean);
  if (field("phone") && !PHONE.test(field("phone"))) {
    setFieldError(form, "phone", "Geçerli bir telefon numarası giriniz.");
    valid = false;
  }
  if (!valid) {
    return;
  }
  whileSubmitting(form, async () => {
    try {
      const business = await api.post("/businesses", {
        name: field("name"),
        type: field("type"),
        city: field("city"),
        district: field("district"),
        address: field("address"),
        phone: field("phone"),
      });
      form.reset();
      showMessage(formMessage, "success", "\"" + business.name + "\" başvurunuz alındı. Durumu aşağıdan takip edebilirsiniz.");
      await loadApplications();
    } catch (error) {
      if (error instanceof ApiError) {
        showFieldErrors(form, error.fieldErrors);
      }
      showError(formMessage, error);
    }
  });
});

if (await startPage()) {
  await loadApplications();
}
