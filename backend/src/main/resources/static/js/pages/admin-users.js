// User administration (GET /admin/users) with search, activation and deactivation (PUT /admin/users/{id}/active),
// and granting and revoking roles (PUT and DELETE /admin/users/{id}/roles/{roleName}). The server refuses the
// administrator's own deactivation (EOD-027) and the revocation of their own YONETICI role (EOD-028).

import { api } from "../api.js";
import { el, hideMessage, showError } from "../dom.js";
import { activeLabel, formatDateTime, ROLE_LABELS } from "../format.js";
import { startPage } from "../layout.js";
import { listPage, showOutcome } from "../list-page.js";
import { actionButton } from "../table.js";

const filter = document.getElementById("filter");
const outcome = document.getElementById("outcome");

function fullName(user) {
  return user.firstName + " " + user.lastName;
}

function run(action, success) {
  return async () => {
    hideMessage(outcome);
    try {
      await action();
      showOutcome(outcome, "success", success);
      await list.reload();
    } catch (error) {
      showError(outcome, error);
    }
  };
}

function roles(user) {
  const chips = el("ul", { className: "role-list" });
  for (const role of user.roles) {
    const label = ROLE_LABELS[role] || role;
    chips.append(
      el("li", { className: "role-chip" }, [
        el("span", { text: label }),
        actionButton("×", {
          className: "role-remove",
          label: label + " rolünü kaldır",
          question: fullName(user) + " kullanıcısının \"" + label + "\" rolü kaldırılsın mı?",
          onClick: run(
            () => api.delete("/admin/users/" + user.id + "/roles/" + role),
            fullName(user) + " kullanıcısının \"" + label + "\" rolü kaldırıldı.",
          ),
        }),
      ]),
    );
  }
  const missing = Object.keys(ROLE_LABELS).filter((role) => !user.roles.includes(role));
  if (missing.length === 0) {
    return chips;
  }
  const selectId = "grant-" + user.id;
  const select = el(
    "select",
    { attrs: { id: selectId } },
    missing.map((role) => el("option", { text: ROLE_LABELS[role], attrs: { value: role } })),
  );
  const grant = actionButton("Rol ver", {
    onClick: () => {
      const role = select.value;
      return run(
        () => api.put("/admin/users/" + user.id + "/roles/" + role),
        fullName(user) + " kullanıcısına \"" + ROLE_LABELS[role] + "\" rolü verildi.",
      )();
    },
  });
  return el("div", {}, [
    chips,
    el("div", { className: "grant-role" }, [
      el("label", { className: "visually-hidden", text: "Verilecek rol", attrs: { for: selectId } }),
      select,
      grant,
    ]),
  ]);
}

function activeToggle(user) {
  const next = !user.active;
  return actionButton(next ? "Aktif yap" : "Pasif yap", {
    kind: next ? "secondary" : "danger",
    question: next
      ? fullName(user) + " hesabı aktif yapılsın mı?"
      : fullName(user) + " hesabı pasif yapılsın mı? Açık oturumu bir sonraki isteğinde kapanır.",
    onClick: run(
      () => api.put("/admin/users/" + user.id + "/active", { active: next }),
      fullName(user) + " hesabı " + (next ? "aktif" : "pasif") + " yapıldı.",
    ),
  });
}

const list = listPage({
  list: document.getElementById("list"),
  message: document.getElementById("list-message"),
  pager: document.getElementById("pager"),
  fetch: (page) =>
    api.get("/admin/users", {
      q: filter.elements.namedItem("q").value.trim(),
      role: filter.elements.namedItem("role").value,
      active: filter.elements.namedItem("active").value,
      page,
      size: 20,
    }),
  rowId: (user) => "user-" + user.id,
  empty: () => "Aramaya uyan kullanıcı yok.",
  columns: [
    { label: "No", value: (user) => user.id, className: "number" },
    { label: "Ad soyad", value: fullName },
    { label: "E-posta", value: (user) => user.email },
    { label: "Telefon", value: (user) => user.phone || "—" },
    { label: "Kayıt", value: (user) => formatDateTime(user.registeredAt) },
    { label: "Roller", value: roles },
    {
      label: "Durum",
      value: (user) =>
        el("span", { className: user.active ? "status-active" : "status-inactive", text: activeLabel(user.active) }),
    },
    { label: "İşlemler", value: activeToggle },
  ],
});

filter.addEventListener("submit", (event) => {
  event.preventDefault();
  hideMessage(outcome);
  list.load(0);
});
for (const name of ["role", "active"]) {
  filter.elements.namedItem(name).addEventListener("change", () => list.load(0));
}

if (await startPage()) {
  await list.load(0);
}
