// The only module that talks to the backend. Every page sends its requests through the functions below.
//
// - Same-origin requests with the session cookie; JSON request and response bodies.
// - Every request other than GET carries the CSRF token: the value of the XSRF-TOKEN cookie in the X-XSRF-TOKEN
//   header. When the cookie is missing, GET /auth/csrf sets it first. The cookie is read again before every request,
//   because sign-in and sign-out replace it.
// - Errors are RFC 9457 problem details. ApiError carries the Turkish detail for the user and the EOD code.
// - 401 sends the user to the sign-in page and keeps the current address to come back to.

const API_BASE = "/api/v1";
const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const LOGIN_PAGE = "/login.html";

export const MESSAGES = Object.freeze({
  network: "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.",
  forbidden: "Bu işlem için yetkiniz yok.",
  unexpected: "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.",
});

/** A failed request: status 0 means the server could not be reached. */
export class ApiError extends Error {
  constructor(status, code, detail, fieldErrors) {
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.detail = detail;
    this.fieldErrors = fieldErrors;
  }
}

function readCookie(name) {
  const prefix = name + "=";
  const cookie = document.cookie.split("; ").find((part) => part.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

async function csrfToken() {
  let token = readCookie(CSRF_COOKIE);
  if (!token) {
    await send("GET", "/auth/csrf", {});
    token = readCookie(CSRF_COOKIE);
  }
  return token;
}

function buildUrl(path, query) {
  const url = new URL(API_BASE + path, window.location.origin);
  if (query) {
    for (const [name, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(name, String(value));
      }
    }
  }
  return url.pathname + url.search;
}

/** The sign-in page with the current address as the page to return to. */
export function loginUrl() {
  const back = window.location.pathname + window.location.search;
  return LOGIN_PAGE + "?next=" + encodeURIComponent(back);
}

/**
 * The page to go to after sign-in. Only a path on this site is accepted ("/..." but not "//..."), so that the
 * parameter cannot send the user to another site; the sign-in page itself is not accepted either.
 */
export function safeNext(value) {
  if (
    typeof value === "string" &&
    value.startsWith("/") &&
    !value.startsWith("//") &&
    !value.startsWith("/\\") &&
    !value.startsWith(LOGIN_PAGE)
  ) {
    return value;
  }
  return "/index.html";
}

async function toApiError(response) {
  let problem = null;
  const type = response.headers.get("Content-Type") || "";
  if (type.includes("json")) {
    try {
      problem = await response.json();
    } catch {
      problem = null;
    }
  }
  const code = problem && typeof problem.code === "string" ? problem.code : null;
  let detail = problem && typeof problem.detail === "string" ? problem.detail : null;
  if (response.status === 403 && (!detail || code === "EOD-403")) {
    detail = MESSAGES.forbidden;
  }
  if (!detail) {
    detail = MESSAGES.unexpected;
  }
  const fieldErrors = problem && Array.isArray(problem.errors) ? problem.errors : [];
  if (code) {
    console.warn("API error", response.status, code);
  }
  return new ApiError(response.status, code, detail, fieldErrors);
}

async function send(method, path, { query, body, redirectOn401 = true } = {}) {
  const headers = { Accept: "application/json, application/problem+json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (method !== "GET") {
    const token = await csrfToken();
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }
  let response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      credentials: "same-origin",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, null, MESSAGES.network, []);
  }
  if (response.ok) {
    if (response.status === 204) {
      return null;
    }
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  }
  const error = await toApiError(response);
  if (response.status === 401 && redirectOn401 && window.location.pathname !== LOGIN_PAGE) {
    window.location.assign(loginUrl());
  }
  throw error;
}

export const api = Object.freeze({
  get: (path, query, options = {}) => send("GET", path, { ...options, query }),
  post: (path, body, options = {}) => send("POST", path, { ...options, body }),
  put: (path, body, options = {}) => send("PUT", path, { ...options, body }),
  delete: (path, options = {}) => send("DELETE", path, options),
});
