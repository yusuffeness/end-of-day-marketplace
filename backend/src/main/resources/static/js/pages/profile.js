// The signed-in user's profile (GET /me, PUT /me) and password (PUT /me/password).

import { api, ApiError } from "../api.js";
import { clearFieldErrors, hideMessage, showError, showFieldErrors, showMessage } from "../dom.js";
import { ROLE_LABELS } from "../format.js";
import { newPassword, optionalPhone, requireValue, sameValue, whileSubmitting } from "../form.js";
import { startPage } from "../layout.js";

const profileForm = document.getElementById("profile-form");
const passwordForm = document.getElementById("password-form");
const profileMessage = document.getElementById("profile-message");
const passwordMessage = document.getElementById("password-message");

function fill(user) {
  profileForm.elements.namedItem("email").value = user.email;
  profileForm.elements.namedItem("firstName").value = user.firstName;
  profileForm.elements.namedItem("lastName").value = user.lastName;
  profileForm.elements.namedItem("phone").value = user.phone || "";
  document.getElementById("profile-roles").textContent = user.roles.map((role) => ROLE_LABELS[role] || role).join(", ");
}

profileForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearFieldErrors(profileForm);
  hideMessage(profileMessage);
  const valid = [
    requireValue(profileForm, "firstName", "Ad zorunludur."),
    requireValue(profileForm, "lastName", "Soyad zorunludur."),
    optionalPhone(profileForm, "phone"),
  ].every(Boolean);
  if (!valid) {
    return;
  }
  const field = (name) => profileForm.elements.namedItem(name).value.trim();
  whileSubmitting(profileForm, async () => {
    try {
      const user = await api.put("/me", {
        firstName: field("firstName"),
        lastName: field("lastName"),
        phone: field("phone") || null,
      });
      fill(user);
      document.querySelector(".account-name").textContent = user.firstName + " " + user.lastName;
      showMessage(profileMessage, "success", "Bilgileriniz kaydedildi.");
    } catch (error) {
      if (error instanceof ApiError) {
        showFieldErrors(profileForm, error.fieldErrors);
      }
      showError(profileMessage, error);
    }
  });
});

passwordForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearFieldErrors(passwordForm);
  hideMessage(passwordMessage);
  const valid = [
    requireValue(passwordForm, "currentPassword", "Mevcut parola zorunludur."),
    newPassword(passwordForm, "newPassword"),
    sameValue(passwordForm, "newPasswordRepeat", "newPassword", "Yeni parolalar aynı değil."),
  ].every(Boolean);
  if (!valid) {
    return;
  }
  const field = (name) => passwordForm.elements.namedItem(name).value;
  whileSubmitting(passwordForm, async () => {
    try {
      await api.put("/me/password", {
        currentPassword: field("currentPassword"),
        newPassword: field("newPassword"),
      });
      passwordForm.reset();
      showMessage(passwordMessage, "success", "Parolanız değiştirildi.");
    } catch (error) {
      if (error instanceof ApiError) {
        showFieldErrors(passwordForm, error.fieldErrors);
      }
      showError(passwordMessage, error);
    }
  });
});

const user = await startPage();
if (user) {
  fill(user);
}
