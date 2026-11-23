// The home page: log in or register, then see my leagues, open a new one or join a friend's.

const loginSection = document.getElementById("login-section");
const appSection = document.getElementById("app-section");

// ---------- Logging in ----------

let registering = false;

function chooseTab(register) {
  registering = register;
  document.getElementById("tab-login").className = register ? "secondary" : "";
  document.getElementById("tab-register").className = register ? "" : "secondary";
  document.getElementById("login-button").textContent = register ? "הרשמה" : "כניסה";
  document.getElementById("password").autocomplete = register ? "new-password" : "current-password";
  hideMessage(document.getElementById("login-message"));
}

document.getElementById("tab-login").addEventListener("click", () => chooseTab(false));
document.getElementById("tab-register").addEventListener("click", () => chooseTab(true));

document.getElementById("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const message = document.getElementById("login-message");
  const button = document.getElementById("login-button");
  const remember = document.getElementById("remember").checked;
  button.disabled = true;
  try {
    const result = await api("POST", registering ? "/api/accounts/register" : "/api/accounts/login", {
      username: document.getElementById("username").value.trim(),
      password: document.getElementById("password").value,
      rememberMe: remember
    });
    saveToken(result.token, remember);
    await showApp(result.account);
  } catch (e) {
    showMessage(message, e.message, "error");
  } finally {
    button.disabled = false;
  }
});

// ---------- My leagues ----------

async function showApp(account) {
  showUser(account);
  loginSection.classList.add("hidden");
  appSection.classList.remove("hidden");
  await loadLeagues();
}

async function loadLeagues() {
  const leagues = await api("GET", "/api/leagues");
  const list = document.getElementById("league-list");
  list.replaceChildren();
  document.getElementById("no-leagues").classList.toggle("hidden", leagues.length > 0);

  for (const league of leagues) {
    const details = el("div", null,
      el("a", { href: "/league.html?id=" + league.id }, league.name),
      el("div", { className: "muted" },
        "הקבוצה שלך: " + league.myTeamName + " · " + league.teams + "/" + league.size + " קבוצות · עונה " + league.season));

    const side = el("div", { style: "text-align: left;" }, el("span", { className: "badge" }, STATUS_NAMES[league.status]));
    if (league.status !== "RUNNING") {
      side.append(el("div", { className: "muted" }, "קוד: ", el("span", { className: "code" }, league.joinCode)));
    }
    list.append(el("li", null, details, side));
  }
}

document.getElementById("create-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const message = document.getElementById("create-message");
  try {
    const league = await api("POST", "/api/leagues", {
      name: document.getElementById("league-name").value.trim(),
      size: Number(document.getElementById("league-size").value),
      kickoffTimes: document.getElementById("kickoff-times").value.replace(/\s/g, ""),
      teamName: document.getElementById("create-team").value.trim()
    });
    location.href = "/league.html?id=" + league.id;
  } catch (e) {
    showMessage(message, e.message, "error");
  }
});

document.getElementById("join-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const message = document.getElementById("join-message");
  try {
    const league = await api("POST", "/api/leagues/join", {
      code: document.getElementById("join-code").value.trim().toUpperCase(),
      teamName: document.getElementById("join-team").value.trim()
    });
    location.href = "/league.html?id=" + league.id;
  } catch (e) {
    showMessage(message, e.message, "error");
  }
});

// ---------- Start ----------

(async function start() {
  if (getToken()) {
    try {
      await showApp(await api("GET", "/api/accounts/me"));
      return;
    } catch (e) {
      clearToken();
    }
  }
  loginSection.classList.remove("hidden");
})();
