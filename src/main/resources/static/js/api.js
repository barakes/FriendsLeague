// Shared by every page: the login token, calling the server, and Hebrew messages.

const TOKEN_KEY = "friendsleague.token";

// "Remember me" keeps the token after the browser closes (localStorage);
// otherwise it lives only until the tab is closed (sessionStorage).
function saveToken(token, remember) {
  clearToken();
  (remember ? localStorage : sessionStorage).setItem(TOKEN_KEY, token);
}

function getToken() {
  return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
}

function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
}

// The server answers in English; the player sees Hebrew.
const MESSAGES = {
  "Wrong username or password": "שם משתמש או סיסמה שגויים",
  "This username is taken": "שם המשתמש כבר תפוס",
  "Username must be 3-20 letters, digits or _": "שם משתמש: 3 עד 20 אותיות, ספרות או _",
  "Password must be at least 6 characters": "הסיסמה צריכה להכיל לפחות 6 תווים",
  "Password is too long": "הסיסמה ארוכה מדי",
  "Please log in": "צריך להתחבר מחדש",
  "A league has 6, 8, 10 or 12 teams": "בליגה יש 6, 8, 10 או 12 קבוצות",
  "Kick-off times look like 21:00": "שעת משחק נכתבת כך: 21:00",
  "Please choose at least one kick-off time": "צריך לבחור לפחות שעת משחק אחת",
  "Rounds on the same day must be at least an hour apart": "בין מחזורים באותו יום צריכה להיות לפחות שעה",
  "Only the league's creator can start the season": "רק מי שפתח את הליגה יכול להתחיל את העונה",
  "The season has already started": "העונה כבר התחילה",
  "There is no league with this code": "אין ליגה עם הקוד הזה",
  "There is no such league": "הליגה לא נמצאה",
  "There is no such team": "הקבוצה לא נמצאה",
  "There is no such footballer": "השחקן לא נמצא",
  "This league has already started; you can join next season": "הליגה כבר התחילה. אפשר להצטרף בעונה הבאה",
  "This league is full": "הליגה מלאה",
  "This team name is taken in this league": "שם הקבוצה כבר תפוס בליגה הזאת",
  "You are already in this league": "כבר הצטרפת לליגה הזאת",
  "You are not in this league": "לא הצטרפת לליגה הזאת",
  "This is not your team": "זו לא הקבוצה שלך",
  "Please choose a formation": "צריך לבחור מערך",
  "Please choose a tactic": "צריך לבחור טקטיקה",
  "Please choose a sponsor": "צריך לבחור נותן חסות",
  "Please choose a footballer": "צריך לבחור שחקן",
  "Every player in the lineup needs a line": "לכל שחקן בהרכב צריך לבחור עמדה",
  "That footballer is not in your squad": "השחקן לא בסגל שלך",
  "This footballer is not in your squad": "השחקן לא בסגל שלך",
  "This footballer is not in your league": "השחקן לא בליגה שלך",
  "This footballer is no longer on the market": "מישהו כבר קנה את השחקן הזה",
  "You don't have enough money for this footballer": "אין מספיק כסף בקופה לשחקן הזה",
  "The sponsor can only be changed between seasons": "אפשר להחליף נותן חסות רק בין עונות",
  "A squad can have at most 22 footballers": "בסגל יכולים להיות לכל היותר 22 שחקנים",
  "A squad needs at least 16 footballers": "בסגל צריכים להיות לפחות 16 שחקנים",
  "You can't sell your last goalkeeper": "אי אפשר למכור את השוער האחרון",
  "League name must be 2-40 letters, digits or spaces": "שם הליגה: 2 עד 40 אותיות, ספרות או רווחים",
  "Team name must be 2-30 letters, digits or spaces": "שם הקבוצה: 2 עד 30 אותיות, ספרות או רווחים",
  "At most 4 rounds a day": "לכל היותר 4 מחזורים ביום"
};

function hebrew(message) {
  if (MESSAGES[message]) {
    return MESSAGES[message];
  }
  if (message && message.startsWith("The formation ")) {
    return "מספר השחקנים בכל קו לא מתאים למערך";
  }
  if (message && message.endsWith(" appears twice in the lineup")) {
    return "אותו שחקן מופיע פעמיים בהרכב";
  }
  return "משהו השתבש. נסו שוב";
}

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

// Calls the server with the token. Returns the JSON answer, or throws ApiError with a Hebrew message.
async function api(method, path, body) {
  const headers = {};
  const token = getToken();
  if (token) {
    headers["Authorization"] = "Bearer " + token;
  }
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  let response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch (e) {
    throw new ApiError(0, "אין חיבור לשרת. אם האתר לא היה בשימוש זמן מה, הוא מתעורר – נסו שוב בעוד דקה");
  }

  if (response.status === 401) {
    clearToken();
  }
  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(response.status, hebrew(data && data.message));
  }
  return data;
}

// For pages that need a logged-in player: returns the account, or sends the player to the home page.
async function requireLogin() {
  if (!getToken()) {
    location.href = "/";
    return null;
  }
  try {
    const account = await api("GET", "/api/accounts/me");
    showUser(account);
    return account;
  } catch (e) {
    location.href = "/";
    return null;
  }
}

// The user's name and a log-out button in the top bar.
function showUser(account) {
  const box = document.getElementById("user-box");
  if (!box) {
    return;
  }
  box.replaceChildren();
  const name = document.createElement("span");
  name.textContent = account.username;
  const out = document.createElement("button");
  out.className = "secondary small";
  out.textContent = "יציאה";
  out.addEventListener("click", async () => {
    try {
      await api("POST", "/api/accounts/logout");
    } finally {
      clearToken();
      location.href = "/";
    }
  });
  box.append(name, out);
}

// Shows a message under a form. kind: "error" or "ok".
function showMessage(element, text, kind) {
  element.textContent = text;
  element.className = "message " + kind;
}

function hideMessage(element) {
  element.textContent = "";
  element.className = "message hidden";
}

// Builds an element: el("td", {className: "name"}, "text", childElement, ...).
// Text is always added with textContent, so names typed by players can never inject HTML.
function el(tag, props, ...children) {
  const element = document.createElement(tag);
  Object.assign(element, props || {});
  for (const child of children) {
    if (child === null || child === undefined) {
      continue;
    }
    element.append(child instanceof Node ? child : String(child));
  }
  return element;
}

// -341000 → "-₪341,000"
function money(amount) {
  const value = Number(amount);
  return (value < 0 ? "-" : "") + "₪" + Math.abs(value).toLocaleString("he-IL");
}

const STATUS_NAMES = { WAITING: "ממתינה לפתיחה", RUNNING: "עונה פעילה", BETWEEN_SEASONS: "בין עונות" };
const POSITION_NAMES = { GOALKEEPER: "שוער", DEFENDER: "הגנה", MIDFIELDER: "קישור", FORWARD: "התקפה" };
const POSITIONS = ["GOALKEEPER", "DEFENDER", "MIDFIELDER", "FORWARD"];
const FORMATIONS = ["F_4_4_2", "F_4_3_3", "F_3_5_2", "F_5_3_2", "F_4_5_1"];
const TACTIC_NAMES = { ATTACKING: "התקפית", BALANCED: "מאוזנת", DEFENSIVE: "הגנתית" };
const SPONSOR_NAMES = {
  SAFE: "בטוח – ₪80,000 בכל מחזור",
  RISKY: "מסוכן – ₪30,000 בכל מחזור ועוד ₪60,000 על כל ניצחון"
};
const MONEY_NAMES = {
  TV_RIGHTS: "זכויות שידור", TICKETS: "כרטיסים", WIN_BONUS: "בונוס ניצחון", DRAW_BONUS: "בונוס תיקו",
  SPONSOR: "נותן חסות", WAGES: "משכורות", SEASON_PRIZE: "פרס סוף עונה", PURCHASE: "קניית שחקן", SALE: "מכירת שחקן"
};

// "F_4_3_3" → "4-3-3"
function formationLabel(formation) {
  return formation.substring(2).replaceAll("_", "-");
}

// How many players each line needs: "F_4_3_3" → {GOALKEEPER: 1, DEFENDER: 4, MIDFIELDER: 3, FORWARD: 3}
function formationCounts(formation) {
  const [defense, midfield, attack] = formation.substring(2).split("_").map(Number);
  return { GOALKEEPER: 1, DEFENDER: defense, MIDFIELDER: midfield, FORWARD: attack };
}
