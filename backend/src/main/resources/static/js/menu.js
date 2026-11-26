// The menu of the top bar, built from this list. A user sees every enabled item of every section that one of their
// roles opens, so a customer who also owns a business sees both sections (report section 6). The list only decides
// what is shown; every page asks the server, which answers 403 to a user without the role.
//
// The menu is built from GET /me on every page load, so a role granted or revoked by an administrator shows up on the
// next page without signing in again. The business application is open to every signed-in user, because the
// business owner role is granted only when the application is approved.

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
    items: [
      { label: "İşletme Yönetimi", href: "/business.html", enabled: true },
      { label: "İşletme Siparişleri", href: "/business-orders.html", enabled: true },
      { label: "İşletme Raporları", href: "/business-reports.html", enabled: true },
    ],
  },
  {
    section: "Yönetici",
    roles: ["YONETICI"],
    items: [
      { label: "Başvurular", href: "/admin-applications.html", enabled: true },
      { label: "Kullanıcılar", href: "/admin-users.html", enabled: true },
      { label: "İşletmeler", href: "/admin-businesses.html", enabled: true },
      { label: "İlan Denetimi", href: "/admin-listings.html", enabled: true },
      { label: "Platform Raporları", href: "/admin-reports.html", enabled: true },
    ],
  },
  {
    section: "Hesap",
    roles: null,
    items: [
      { label: "Profil", href: "/profile.html", enabled: true },
      { label: "İşletme Başvurusu", href: "/business-apply.html", enabled: true },
    ],
  },
]);

/** The sections and items a user with these roles sees; a section with roles null is shown to every signed-in user. */
export function menuFor(roles) {
  return MENU.filter((section) => section.roles === null || section.roles.some((role) => roles.includes(role)))
    .map((section) => ({ ...section, items: section.items.filter((item) => item.enabled) }))
    .filter((section) => section.items.length > 0);
}
