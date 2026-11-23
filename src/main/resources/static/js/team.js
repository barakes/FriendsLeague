// The team page: squad, lineup on a pitch, and for the manager also editing, selling, sponsor and money history.
// Other members of the league can look at the team but not change it.

const teamId = new URLSearchParams(location.search).get("id");

let team = null;     // The last answer of /api/teams/{id}
let league = null;   // The league summary, for its name and status
let draft = null;    // The manager's unsaved changes: { formation, tactic, lines: Map(footballerId → line or null) }
let sellArmed = null;  // The footballer whose "sell" button was clicked once and waits for a second click

// ---------- Loading ----------

async function load() {
  team = await api("GET", "/api/teams/" + teamId);
  league = (await api("GET", "/api/leagues/" + team.leagueId)).league;
  resetDraft();
  showAll();
  if (team.mine) {
    loadMoney();
  }
}

function resetDraft() {
  const lines = new Map();
  for (const footballer of team.squad) {
    lines.set(footballer.id, footballer.line);
  }
  draft = { formation: team.formation, tactic: team.tactic, lines };
}

function showAll() {
  showHeader();
  showControls();
  showPitch();
  showSquad();
  showSponsor();
}

// ---------- Header ----------

function showHeader() {
  document.title = "ליגת חברים – " + team.name;
  document.getElementById("team-name").textContent = team.name;
  const leagueLink = document.getElementById("league-link");
  leagueLink.textContent = league.name;
  leagueLink.href = "/league.html?id=" + league.id;
  document.getElementById("team-kind").textContent = team.computer ? "· קבוצת מחשב" : team.mine ? "· הקבוצה שלך" : "";

  const market = document.getElementById("market-link");
  market.href = "/market.html?league=" + league.id + "&team=" + team.id;
  market.classList.toggle("hidden", !team.mine || league.status === "WAITING");

  const stats = document.getElementById("stats");
  stats.replaceChildren(stat("שחקנים בסגל", team.squad.length));
  if (team.mine) {
    stats.append(
      stat("בקופה", money(team.money), team.money < 0),
      stat("משכורות למחזור", money(team.wagesPerRound)));
    if (team.roundsInDebt > 0) {
      stats.append(stat("מחזורים ברצף בחוב", team.roundsInDebt + " מתוך 3", true));
    }
  }
}

function stat(label, value, warning) {
  return el("div", { className: "stat" + (warning ? " warning" : "") },
    el("div", { className: "label" }, label), el("div", { className: "value" }, value));
}

// ---------- Formation and tactic ----------

function showControls() {
  document.getElementById("lineup-controls").classList.toggle("hidden", !team.mine);
  document.getElementById("lineup-buttons").classList.toggle("hidden", !team.mine);
  document.getElementById("lineup-note").textContent = team.computer
    ? "קבוצת מחשב בוחרת לפני כל משחק את ההרכב והמערך החזקים ביותר שלה."
    : team.mine
      ? "ההרכב ננעל בשריקת הפתיחה: שינוי אחרי שהמשחק התחיל ייכנס לתוקף מהמשחק הבא."
      : "מערך " + formationLabel(team.formation) + " · טקטיקה " + TACTIC_NAMES[team.tactic];
  if (!team.mine) {
    return;
  }

  const formation = document.getElementById("formation");
  formation.replaceChildren(...FORMATIONS.map(f => el("option", { value: f, selected: f === draft.formation }, formationLabel(f))));
  const tactic = document.getElementById("tactic");
  tactic.replaceChildren(...Object.keys(TACTIC_NAMES).map(t => el("option", { value: t, selected: t === draft.tactic }, TACTIC_NAMES[t])));
  showLineCounts();
}

document.getElementById("formation").addEventListener("change", (event) => {
  draft.formation = event.target.value;
  showLineCounts();
  showPitch();
});

document.getElementById("tactic").addEventListener("change", (event) => {
  draft.tactic = event.target.value;
});

// "הגנה 4/4" for every line: how many are chosen, out of how many the formation needs.
function showLineCounts() {
  const needed = formationCounts(draft.formation);
  const chosen = countLines();
  document.getElementById("line-counts").replaceChildren(...POSITIONS.map(line =>
    el("span", { className: chosen[line] === needed[line] ? "" : "wrong" },
      POSITION_NAMES[line] + " " + chosen[line] + "/" + needed[line])));
  document.getElementById("save-lineup").disabled = POSITIONS.some(line => chosen[line] !== needed[line]);
}

function countLines() {
  const counts = { GOALKEEPER: 0, DEFENDER: 0, MIDFIELDER: 0, FORWARD: 0 };
  for (const line of draft.lines.values()) {
    if (line) {
      counts[line]++;
    }
  }
  return counts;
}

// ---------- The pitch ----------

function showPitch() {
  const lines = team.mine ? draft.lines : new Map(team.squad.map(f => [f.id, f.line]));
  const needed = formationCounts(team.mine ? draft.formation : team.formation);
  const pitch = document.getElementById("pitch");
  pitch.replaceChildren();

  // Attack at the top of the pitch, the goalkeeper at the bottom.
  for (const line of [...POSITIONS].reverse()) {
    const players = team.squad.filter(f => lines.get(f.id) === line).sort((a, b) => b.rating - a.rating);
    const row = el("div", { className: "pitch-line" });
    for (const player of players) {
      const outOfPosition = player.position !== line;
      row.append(el("div", {
        className: "shirt" + (outOfPosition ? " out-of-position" : ""),
        title: outOfPosition ? "משחק מחוץ לעמדה שלו: " + POSITION_NAMES[player.position] : ""
      }, el("div", { className: "rating" }, player.rating), player.name));
    }
    for (let i = players.length; i < needed[line]; i++) {
      row.append(el("div", { className: "shirt empty" }, el("div", { className: "rating" }, "?"), POSITION_NAMES[line]));
    }
    pitch.append(row);
  }
}

// ---------- The squad ----------

function showSquad() {
  document.getElementById("sell-header").classList.toggle("hidden", !team.mine);
  const order = (a, b) => POSITIONS.indexOf(a.position) - POSITIONS.indexOf(b.position) || b.rating - a.rating;
  const body = document.getElementById("squad-body");
  body.replaceChildren();

  for (const player of [...team.squad].sort(order)) {
    const row = el("tr", null,
      el("td", { className: "name" }, player.name),
      el("td", null, POSITION_NAMES[player.position]),
      el("td", { className: "rating-cell" }, player.rating),
      el("td", null, player.age),
      el("td", null, team.mine ? lineSelect(player) : (player.line ? POSITION_NAMES[player.line] : "ספסל")));
    if (team.mine) {
      row.append(el("td", null, sellButton(player)));
    }
    body.append(row);
  }
}

function lineSelect(player) {
  const select = el("select", { ariaLabel: "איפה " + player.name + " משחק" },
    el("option", { value: "" }, "ספסל"),
    ...POSITIONS.map(line => el("option", { value: line }, POSITION_NAMES[line])));
  select.value = draft.lines.get(player.id) || "";
  select.addEventListener("change", () => {
    draft.lines.set(player.id, select.value || null);
    showLineCounts();
    showPitch();
  });
  return select;
}

// First click shows the price; a second click sells.
function sellButton(player) {
  const armed = sellArmed === player.id;
  const button = el("button", { className: "small " + (armed ? "danger" : "secondary") },
    armed ? "למכור ב־" + money(player.sellPrice) + "?" : "מכירה");
  button.addEventListener("click", async () => {
    if (!armed) {
      sellArmed = player.id;
      showSquad();
      return;
    }
    sellArmed = null;
    try {
      team = await api("POST", "/api/teams/" + teamId + "/sell", { footballerId: player.id });
      resetDraft();
      showAll();
      loadMoney();
      showMessage(document.getElementById("squad-message"), player.name + " נמכר ב־" + money(player.sellPrice), "ok");
    } catch (e) {
      showSquad();
      showMessage(document.getElementById("squad-message"), e.message, "error");
    }
  });
  return button;
}

// ---------- Saving the lineup ----------

document.getElementById("save-lineup").addEventListener("click", async () => {
  const slots = [];
  for (const [footballerId, line] of draft.lines) {
    if (line) {
      slots.push({ footballerId, line });
    }
  }
  await saveWith("PUT", "/api/teams/" + teamId + "/lineup",
    { formation: draft.formation, tactic: draft.tactic, slots }, "ההרכב נשמר");
});

document.getElementById("best-lineup").addEventListener("click", async () => {
  const tactic = draft.tactic;
  await saveWith("POST", "/api/teams/" + teamId + "/lineup/best", { formation: draft.formation },
    "נבחר ההרכב הכי טוב במערך " + formationLabel(draft.formation));
  // The button keeps the tactic the manager chose; if it changed, save it too.
  if (tactic !== team.tactic) {
    draft.tactic = tactic;
    document.getElementById("save-lineup").click();
  }
});

async function saveWith(method, path, body, okText) {
  const message = document.getElementById("lineup-message");
  try {
    team = await api(method, path, body);
    resetDraft();
    showAll();
    showMessage(message, okText, "ok");
  } catch (e) {
    showMessage(message, e.message, "error");
  }
}

// ---------- Sponsor and money ----------

function showSponsor() {
  document.getElementById("money-section").classList.toggle("hidden", !team.mine);
  if (!team.mine) {
    return;
  }
  const select = document.getElementById("sponsor");
  select.replaceChildren(...Object.keys(SPONSOR_NAMES).map(s => el("option", { value: s }, SPONSOR_NAMES[s])));
  select.value = team.sponsor;
  const running = league.status === "RUNNING";
  select.disabled = running;
  document.getElementById("sponsor-note").textContent = running
    ? "החוזה עם נותן החסות הוא לעונה שלמה. אפשר להחליף אותו בין עונות."
    : "הבחירה היא לכל העונה. \"מסוכן\" משתלם לקבוצה שמנצחת הרבה.";
}

document.getElementById("sponsor").addEventListener("change", async (event) => {
  const message = document.getElementById("sponsor-message");
  try {
    team = await api("PUT", "/api/teams/" + teamId + "/sponsor", { sponsor: event.target.value });
    showMessage(message, "נותן החסות נשמר", "ok");
  } catch (e) {
    event.target.value = team.sponsor;
    showMessage(message, e.message, "error");
  }
});

async function loadMoney() {
  const records = await api("GET", "/api/teams/" + teamId + "/money");
  document.getElementById("no-money").classList.toggle("hidden", records.length > 0);
  document.getElementById("money-body").replaceChildren(...records.map(record =>
    el("tr", null,
      el("td", { className: "name" }, MONEY_NAMES[record.kind], record.note ? el("span", { className: "muted" }, " · " + record.note) : null),
      el("td", null, record.round ?? "–"),
      el("td", { dir: "ltr", style: "color: " + (record.amount < 0 ? "var(--bad)" : "var(--good)") }, money(record.amount)))));
}

// ---------- Start ----------

(async function start() {
  if (!teamId) {
    location.href = "/";
    return;
  }
  if (await requireLogin()) {
    try {
      await load();
    } catch (e) {
      document.querySelector("main").replaceChildren(el("p", { className: "message error" }, e.message));
    }
  }
})();
