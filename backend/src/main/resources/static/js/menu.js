// The menu of the top bar, built from this list. A user sees every enabled item of every section that one of their
// roles opens, so a customer who also owns a business sees both sections (report section 6).
//
// To add the pages of the business owner and the administrator, add their items here (or set enabled to true).

export const MENU = Object.freeze([
  {
    section: "Müşteri",
    roles: ["MUSTERI"],
    items: [
      { label: "Paketler", href: "/index.html", enabled: true },
      { label: "Sepet", href: "/cart.html", enabled: true },
      { label: "Siparişlerim", href: "/orders.html", enabled: true },
    ],
  },
  {
    section: "İşletme",
    roles: ["ISLETME_YETKILISI"],
    items: [],
  },
  {
    section: "Yönetici",
    roles: ["YONETICI"],
    items: [],
  },
  {
    section: "Hesap",
    roles: null,
    items: [{ label: "Profil", href: "/profile.html", enabled: true }],
  },
]);

/** The sections and items a user with these roles sees; a section with roles null is shown to every signed-in user. */
export function menuFor(roles) {
  return MENU.filter((section) => section.roles === null || section.roles.some((role) => roles.includes(role)))
    .map((section) => ({ ...section, items: section.items.filter((item) => item.enabled) }))
    .filter((section) => section.items.length > 0);
}
