// The transfer market: free agents of the league, with filters, and buying in two clicks.
// The list is loaded again every 30 seconds, because friends and computer teams buy too.

const params = new URLSearchParams(location.search);
const leagueId = params.get("league");
const teamId = params.get("team");

const MAX_SQUAD = 22;
const REFRESH = 30000;

let team = null;
let market = [];
let buyArmed = null;   // The footballer whose "buy" button was clicked once
let timer = null;

async function load() {
  clearTimeout(timer);
  try {
    const [teamView, details, freeAgents] = await Promise.all([
      api("GET", "/api/teams/" + teamId),
      api("GET", "/api/leagues/" + leagueId),
      api("GET", "/api/leagues/" + leagueId + "/market")
    ]);
    team = teamView;
    market = freeAgents;
    showHeader(details.league);
    showMarket();
  } catch (e) {
    if (e.status === 401) {
      location.href = "/";
      return;
    }
    showMessage(document.getElementById("market-message"), e.message, "error");
  }
  timer = setTimeout(load, REFRESH);
}

function showHeader(league) {
  const leagueLink = document.getElementById("league-link");
  leagueLink.textContent = league.name;
  leagueLink.href = "/league.html?id=" + league.id;
  const teamLink = document.getElementById("team-link");
  teamLink.textContent = team.name;
  teamLink.href = "/team.html?id=" + team.id;

  document.getElementById("stats").replaceChildren(
    stat("בקופה", money(team.money), team.money < 0),
    stat("שחקנים בסגל (עד " + MAX_SQUAD + ")", team.squad.length),
    stat("משכורות למחזור", money(team.wagesPerRound)));
}

function stat(label, value, warning) {
  return el("div", { className: "stat" + (warning ? " warning" : "") },
    el("div", { className: "label" }, label), el("div", { className: "value" }, value));
}

// ---------- The list ----------

const positionFilter = document.getElementById("position-filter");
positionFilter.append(...POSITIONS.map(p => el("option", { value: p }, POSITION_NAMES[p])));
for (const id of ["position-filter", "sort", "affordable"]) {
  document.getElementById(id).addEventListener("change", showMarket);
}

const SORTS = {
  rating: (a, b) => b.rating - a.rating,
  price: (a, b) => a.price - b.price,
  age: (a, b) => a.age - b.age
};

function showMarket() {
  const position = positionFilter.value;
  const onlyAffordable = document.getElementById("affordable").checked;
  const shown = market
    .filter(f => !position || f.position === position)
    .filter(f => !onlyAffordable || f.price <= team.money)
    .sort(SORTS[document.getElementById("sort").value]);

  document.getElementById("empty").classList.toggle("hidden", shown.length > 0);
  document.getElementById("market-body").replaceChildren(...shown.map(footballer => el("tr", null,
    el("td", { className: "name" }, footballer.name),
    el("td", null, POSITION_NAMES[footballer.position]),
    el("td", { className: "rating-cell" }, footballer.rating),
    el("td", null, footballer.age),
    el("td", { dir: "ltr" }, money(footballer.price)),
    el("td", { dir: "ltr" }, money(footballer.wage)),
    el("td", null, buyButton(footballer)))));
}

// First click shows the price; a second click buys.
function buyButton(footballer) {
  const full = team.squad.length >= MAX_SQUAD;
  const tooExpensive = footballer.price > team.money;
  const armed = buyArmed === footballer.id;

  const button = el("button", { className: "small" + (armed ? " danger" : ""), disabled: full || tooExpensive },
    ...(armed ? ["לקנות ב־", el("bdi", null, money(footballer.price)), "?"] : ["קנייה"]));
  if (full) {
    button.title = "הסגל מלא: אפשר למכור שחקן בדף הקבוצה";
  } else if (tooExpensive) {
    button.title = "אין מספיק כסף בקופה";
  }

  button.addEventListener("click", async () => {
    if (!armed) {
      buyArmed = footballer.id;
      showMarket();
      return;
    }
    buyArmed = null;
    const message = document.getElementById("market-message");
    try {
      await api("POST", "/api/teams/" + teamId + "/buy", { footballerId: footballer.id });
      showMessage(message, footballer.name + " הצטרף לקבוצה! הוא על הספסל – אפשר להכניס אותו להרכב בדף הקבוצה.", "ok");
    } catch (e) {
      showMessage(message, e.message, "error");
    }
    load();
  });
  return button;
}

// ---------- Start ----------

(async function start() {
  if (!leagueId || !teamId) {
    location.href = "/";
    return;
  }
  if (await requireLogin()) {
    load();
  }
})();
