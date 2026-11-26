// Sign-in and registration (POST /auth/login, POST /auth/register). After sign-in the user returns to the page that
// sent them here (the next parameter), if it is a page of this site.

import { api, ApiError, safeNext } from "../api.js";
import { clearFieldErrors, hideMessage, showError, showFieldErrors, showMessage } from "../dom.js";
import { newPassword, optionalPhone, requireEmail, requireValue, sameValue, whileSubmitting } from "../form.js";

const loginForm = document.getElementById("login-form");
const registerForm = document.getElementById("register-form");
const loginTab = document.getElementById("tab-login");
const registerTab = document.getElementById("tab-register");
const message = document.getElementById("auth-message");
const next = safeNext(new URLSearchParams(window.location.search).get("next"));

function showTab(name) {
  const login = name === "login";
  loginForm.hidden = !login;
  registerForm.hidden = login;
  loginTab.setAttribute("aria-selected", String(login));
  registerTab.setAttribute("aria-selected", String(!login));
}

loginTab.addEventListener("click", () => {
  hideMessage(message);
  showTab("login");
});
registerTab.addEventListener("click", () => {
  hideMessage(message);
  showTab("register");
});

loginForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearFieldErrors(loginForm);
  hideMessage(message);
  const valid = [
    requireEmail(loginForm, "email"),
    requireValue(loginForm, "password", "Parola zorunludur."),
  ].every(Boolean);
  if (!valid) {
    return;
  }
  whileSubmitting(loginForm, async () => {
    try {
      await api.post(
        "/auth/login",
        { email: loginForm.elements.namedItem("email").value.trim(), password: loginForm.elements.namedItem("password").value },
        { redirectOn401: false },
      );
      window.location.assign(next);
    } catch (error) {
      if (error instanceof ApiError) {
        showFieldErrors(loginForm, error.fieldErrors);
      }
      showError(message, error);
      loginForm.elements.namedItem("password").value = "";
    }
  });
});

registerForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearFieldErrors(registerForm);
  hideMessage(message);
  const valid = [
    requireValue(registerForm, "firstName", "Ad zorunludur."),
    requireValue(registerForm, "lastName", "Soyad zorunludur."),
    requireEmail(registerForm, "email"),
    optionalPhone(registerForm, "phone"),
    newPassword(registerForm, "password"),
    sameValue(registerForm, "passwordRepeat", "password", "Parolalar aynı değil."),
  ].every(Boolean);
  if (!valid) {
    return;
  }
  const field = (name) => registerForm.elements.namedItem(name).value;
  whileSubmitting(registerForm, async () => {
    try {
      await api.post(
        "/auth/register",
        {
          firstName: field("firstName").trim(),
          lastName: field("lastName").trim(),
          email: field("email").trim(),
          phone: field("phone").trim() || null,
          password: field("password"),
        },
        { redirectOn401: false },
      );
      const email = field("email").trim();
      registerForm.reset();
      showTab("login");
      loginForm.elements.namedItem("email").value = email;
      showMessage(message, "success", "Hesabınız oluşturuldu. Şimdi giriş yapabilirsiniz.");
      loginForm.elements.namedItem("password").focus();
    } catch (error) {
      if (error instanceof ApiError) {
        showFieldErrors(registerForm, error.fieldErrors);
      }
      showError(message, error);
    }
  });
});

// A user who is already signed in goes straight on.
try {
  await api.get("/me", undefined, { redirectOn401: false });
  window.location.replace(next);
} catch {
  // Not signed in: stay on this page.
}
