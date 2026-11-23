// The league page: the table, and one round at a time with a live broadcast.
// The page asks the server again every few seconds while a game is live, and every 30 seconds otherwise.

const leagueId = new URLSearchParams(location.search).get("id");

const FAST = 3000;     // During a broadcast: one game minute every 3 seconds.
const SLOW = 30000;

let details = null;     // The last answer of /api/leagues/{id}
let shownRound = null;  // The round on screen. null = follow the current round.
let timer = null;
const seenGoals = new Set();   // To highlight goals that appear during the broadcast.
let firstLoad = true;
let followed = null;    // The last round shown while following the season.

const israelTime = new Intl.DateTimeFormat("he-IL", {
  timeZone: "Asia/Jerusalem", weekday: "long", day: "numeric", month: "numeric", hour: "2-digit", minute: "2-digit"
});

// ---------- Loading ----------

async function refresh() {
  clearTimeout(timer);
  let delay = SLOW;
  try {
    details = await api("GET", "/api/leagues/" + leagueId);
    showLeague();
    showSeasonEnd();
    showChampions();
    showTable();
    delay = await showRound();
  } catch (e) {
    if (e.status === 401) {
      location.href = "/";
      return;
    }
    document.getElementById("games").replaceChildren(el("p", { className: "message error" }, e.message));
  }
  timer = setTimeout(refresh, delay);
}

// ---------- The header ----------

function showLeague() {
  const league = details.league;
  document.title = "ליגת חברים – " + league.name;
  document.getElementById("league-name").textContent = league.name;
  document.getElementById("league-info").textContent =
    STATUS_NAMES[league.status] + " · עונה " + league.season + " · " + league.teams + "/" + league.size + " קבוצות" +
    " · משחקים ב־" + league.kickoffTimes.replaceAll(",", ", ");

  document.getElementById("my-team-link").href = "/team.html?id=" + league.myTeamId;
  const market = document.getElementById("market-link");
  market.href = "/market.html?league=" + league.id + "&team=" + league.myTeamId;
  market.classList.toggle("hidden", league.status === "WAITING");

  const waiting = league.status === "WAITING";
  document.getElementById("waiting-card").classList.toggle("hidden", !waiting);
  document.getElementById("round-card").classList.toggle("hidden", waiting);
  if (waiting) {
    document.getElementById("join-code").textContent = league.joinCode;
    const empty = league.size - league.teams;
    document.getElementById("waiting-text").textContent = league.owner
      ? "כשכולם בפנים, לחצו על התחלת העונה. " + (empty > 0 ? empty + " המקומות הפנויים יתמלאו בקבוצות מחשב." : "")
      : "מחכים שמי שפתח את הליגה יתחיל את העונה.";
    document.getElementById("start-box").classList.toggle("hidden", !league.owner);
  }
}

document.getElementById("start-button").addEventListener("click", async () => {
  const button = document.getElementById("start-button");
  button.disabled = true;
  try {
    await api("POST", "/api/leagues/" + leagueId + "/start");
    await refresh();
  } catch (e) {
    showMessage(document.getElementById("start-message"), e.message, "error");
  } finally {
    button.disabled = false;
  }
});

// ---------- Between seasons ----------

function showSeasonEnd() {
  const league = details.league;
  const ended = league.status === "BETWEEN_SEASONS";
  document.getElementById("season-end-card").classList.toggle("hidden", !ended);
  if (!ended) {
    return;
  }

  document.getElementById("season-end-title").textContent = "עונה " + league.season + " הסתיימה";
  const champion = details.champions.find(c => c.season === league.season);
  document.getElementById("champion-text").textContent = champion
    ? "🏆 האלופה: " + champion.teamName + " עם " + champion.points + " נקודות"
    : "";

  const computers = details.table.filter(row => row.computer).length;
  document.getElementById("invite-text").classList.toggle("hidden", computers === 0);
  document.getElementById("between-code").textContent = league.joinCode;
  document.getElementById("next-season-box").classList.toggle("hidden", !league.owner);
}

document.getElementById("next-season-button").addEventListener("click", async () => {
  const button = document.getElementById("next-season-button");
  button.disabled = true;
  try {
    await api("POST", "/api/leagues/" + leagueId + "/start");
    shownRound = null;
    followed = null;
    await refresh();
  } catch (e) {
    showMessage(document.getElementById("next-season-message"), e.message, "error");
  } finally {
    button.disabled = false;
  }
});

function showChampions() {
  document.getElementById("champions-card").classList.toggle("hidden", details.champions.length === 0);
  document.getElementById("champions-list").replaceChildren(...details.champions.map(champion =>
    el("li", null,
      el("span", null, "עונה " + champion.season + ": ", el("strong", null, champion.teamName)),
      el("span", { className: "muted" }, champion.points + " נקודות"))));
}

// ---------- The table ----------

function showTable() {
  const body = document.getElementById("table-body");
  body.replaceChildren();
  for (const row of details.table) {
    const name = el("td", { className: "name" },
      el("a", { href: "/team.html?id=" + row.teamId }, row.teamName),
      row.computer ? el("span", { className: "muted" }, " (מחשב)") : null,
      row.pointsDeducted > 0 ? el("div", { className: "points-off" }, "‎-" + row.pointsDeducted + " נק' על חוב") : null);
    body.append(el("tr", { className: row.mine ? "mine" : "" },
      el("td", null, row.position), name, el("td", null, row.played), el("td", null, row.wins),
      el("td", null, row.draws), el("td", null, row.losses),
      el("td", { dir: "ltr", className: "goals-column" }, row.goalsFor + ":" + row.goalsAgainst),
      el("td", { dir: "ltr" }, row.goalDifference > 0 ? "+" + row.goalDifference : row.goalDifference),
      el("td", null, el("strong", null, row.points))));
  }
}

// ---------- One round ----------

// Returns how soon to ask again.
async function showRound() {
  if (details.totalRounds === 0) {
    return SLOW;
  }
  // When a round ends while the page is open, stay on it so the final scores remain on screen.
  if (shownRound === null && followed !== null && details.currentRound > followed) {
    shownRound = followed;
  }
  const round = shownRound ?? details.currentRound;
  if (shownRound === null) {
    followed = round;
  }
  document.getElementById("round-title").textContent = "מחזור " + round + " מתוך " + details.totalRounds;
  document.getElementById("previous-round").disabled = round <= 1;
  document.getElementById("next-round").disabled = round >= details.totalRounds;

  const games = await api("GET", "/api/leagues/" + leagueId + "/rounds/" + round);
  const box = document.getElementById("games");
  box.replaceChildren(...games.map(showGame));
  firstLoad = false;

  const live = games.some(game => game.status === "LIVE");
  const soon = games.some(game => game.status === "SCHEDULED" && new Date(game.kickoffAt) - Date.now() < SLOW);
  return live || soon ? FAST : SLOW;
}

function showGame(game) {
  const myTeam = details.league.myTeamId;
  const home = el("div", { className: "home" + (game.home.id === myTeam ? " mine-team" : "") }, game.home.name);
  const away = el("div", { className: "away" + (game.away.id === myTeam ? " mine-team" : "") }, game.away.name);

  // The score is written left to right, so the away team's goals come first: each number is next to its team.
  const goals = game.awayScore + " : " + game.homeScore;
  let score;
  if (game.status === "SCHEDULED") {
    score = el("div", { className: "score" }, "– : –", el("span", { className: "when" }, israelTime.format(new Date(game.kickoffAt))));
  } else if (game.status === "LIVE") {
    score = el("div", { className: "score" }, goals,
      el("span", { className: "minute" }, "שידור חי · דקה " + game.minute));
  } else {
    score = el("div", { className: "score" }, goals, el("span", { className: "when" }, "סיום"));
  }

  const row = el("div", { className: "game" }, home, score, away);
  if (game.goals.length > 0) {
    row.append(goalList(game));
  }
  return row;
}

function goalList(game) {
  const homeGoals = el("div", { className: "home-goals" });
  const awayGoals = el("div", { className: "away-goals" });
  for (const goal of game.goals) {
    const key = game.id + ":" + goal.minute + ":" + goal.scorer;
    const line = el("div", null, "⚽ " + goal.scorer + " " + goal.minute + "'");
    // A goal that appeared since the last refresh flashes, unless the page just opened.
    if (!seenGoals.has(key)) {
      seenGoals.add(key);
      if (!firstLoad && game.status === "LIVE") {
        line.className = "new-goal";
      }
    }
    (goal.side === "HOME" ? homeGoals : awayGoals).append(line);
  }
  return el("div", { className: "goals" }, homeGoals, awayGoals);
}

document.getElementById("previous-round").addEventListener("click", () => moveRound(-1));
document.getElementById("next-round").addEventListener("click", () => moveRound(1));

function moveRound(step) {
  const round = (shownRound ?? details.currentRound) + step;
  // Back on the current round, the page follows the season again by itself.
  shownRound = round === details.currentRound ? null : round;
  firstLoad = true;
  refresh();
}

// ---------- Start ----------

(async function start() {
  if (!leagueId) {
    location.href = "/";
    return;
  }
  if (await requireLogin()) {
    refresh();
  }
})();
