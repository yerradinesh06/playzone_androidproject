/**
 * PS 22 Tournament Manager - Google Apps Script Backend
 *
 * Implements the SheetContract contract:
 * - Teams: teamId, tournamentId, name, shortName, color, seed
 * - Players: playerId, teamId, name, number, position
 * - Fixtures: fixtureId, tournamentId, round, homeTeamId, awayTeamId, kickoffAt, venue, status, homeScore, awayScore, version, homeSourceId, awaySourceId
 * - Results: resultId, fixtureId, homeScore, awayScore, state, version, submittedBy, submittedAt
 * - Standings: rank, teamId, played, wins, draws, losses, goalsFor, goalsAgainst, goalDifference, points, updatedAt
 */

function doGet(e) {
  var action = e.parameter.action || "standings";
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  if (action === "standings") {
    var sheet = ss.getSheetByName("Standings");
    if (!sheet) return responseJson({ error: "Standings sheet not found" });
    var data = sheet.getDataRange().getValues();
    var headers = data[0];
    var rows = data.slice(1).map(function(row) {
      var obj = {};
      headers.forEach(function(h, i) { obj[h] = row[i]; });
      return obj;
    });
    return responseJson({ tournamentId: e.parameter.tournamentId || "ALL", standings: rows });
  }

  return responseJson({ status: "PS 22 Apps Script Active" });
}

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    var action = body.action;
    var ss = SpreadsheetApp.getActiveSpreadsheet();

    if (action === "submitResult") {
      return handleSubmitResult(ss, body);
    }

    return responseJson({ error: "Unsupported action" }, 400);
  } catch (err) {
    return responseJson({ error: err.toString() }, 500);
  }
}

function handleSubmitResult(ss, body) {
  var fixtureId = body.fixtureId;
  var homeScore = parseInt(body.homeScore, 10);
  var awayScore = parseInt(body.awayScore, 10);
  var submittedBy = body.submittedBy || "ORGANISER";

  if (!fixtureId || isNaN(homeScore) || isNaN(awayScore)) {
    return responseJson({ error: "Invalid score submission payload" }, 400);
  }

  // Record result
  var resultsSheet = ss.getSheetByName("Results");
  if (!resultsSheet) {
    resultsSheet = ss.insertSheet("Results");
    resultsSheet.appendRow(["resultId", "fixtureId", "homeScore", "awayScore", "state", "version", "submittedBy", "submittedAt"]);
  }

  var resultId = "RES-" + Date.now();
  var timestamp = new Date().toISOString();
  resultsSheet.appendRow([resultId, fixtureId, homeScore, awayScore, "PUBLISHED", 1, submittedBy, timestamp]);

  // Update Fixture
  var fixturesSheet = ss.getSheetByName("Fixtures");
  if (fixturesSheet) {
    var data = fixturesSheet.getDataRange().getValues();
    for (var i = 1; i < data.length; i++) {
      if (data[i][0] === fixtureId) {
        fixturesSheet.getRange(i + 1, 8).setValue("PUBLISHED"); // status column
        fixturesSheet.getRange(i + 1, 9).setValue(homeScore);   // homeScore column
        fixturesSheet.getRange(i + 1, 10).setValue(awayScore);  // awayScore column
        break;
      }
    }
  }

  // Recalculate Standings
  recalculateStandings(ss);

  return responseJson({ success: true, resultId: resultId });
}

function recalculateStandings(ss) {
  var teamsSheet = ss.getSheetByName("Teams");
  var fixturesSheet = ss.getSheetByName("Fixtures");
  var standingsSheet = ss.getSheetByName("Standings");

  if (!teamsSheet || !fixturesSheet) return;
  if (!standingsSheet) {
    standingsSheet = ss.insertSheet("Standings");
  }

  var teamsData = teamsSheet.getDataRange().getValues().slice(1);
  var fixturesData = fixturesSheet.getDataRange().getValues().slice(1);

  var table = {};
  teamsData.forEach(function(row) {
    var teamId = row[0];
    var name = row[2];
    table[teamId] = { teamId: teamId, name: name, played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, points: 0 };
  });

  fixturesData.forEach(function(f) {
    var status = f[7];
    var homeId = f[3];
    var awayId = f[4];
    var homeScore = parseInt(f[8], 10);
    var awayScore = parseInt(f[9], 10);

    if (status === "PUBLISHED" && !isNaN(homeScore) && !isNaN(awayScore)) {
      if (table[homeId] && table[awayId]) {
        table[homeId].played++;
        table[awayId].played++;
        table[homeId].goalsFor += homeScore;
        table[homeId].goalsAgainst += awayScore;
        table[awayId].goalsFor += awayScore;
        table[awayId].goalsAgainst += homeScore;

        if (homeScore > awayScore) {
          table[homeId].wins++;
          table[homeId].points += 3;
          table[awayId].losses++;
        } else if (homeScore < awayScore) {
          table[awayId].wins++;
          table[awayId].points += 3;
          table[homeId].losses++;
        } else {
          table[homeId].draws++;
          table[homeId].points += 1;
          table[awayId].draws++;
          table[awayId].points += 1;
        }
      }
    }
  });

  var sorted = Object.keys(table).map(function(k) { return table[k]; }).sort(function(a, b) {
    if (b.points !== a.points) return b.points - a.points;
    var gdA = a.goalsFor - a.goalsAgainst;
    var gdB = b.goalsFor - b.goalsAgainst;
    if (gdB !== gdA) return gdB - gdA;
    if (b.goalsFor !== a.goalsFor) return b.goalsFor - a.goalsFor;
    return a.name.localeCompare(b.name);
  });

  standingsSheet.clear();
  standingsSheet.appendRow(["rank", "teamId", "played", "wins", "draws", "losses", "goalsFor", "goalsAgainst", "goalDifference", "points", "updatedAt"]);

  var now = new Date().toISOString();
  sorted.forEach(function(item, idx) {
    var gd = item.goalsFor - item.goalsAgainst;
    standingsSheet.appendRow([idx + 1, item.teamId, item.played, item.wins, item.draws, item.losses, item.goalsFor, item.goalsAgainst, gd, item.points, now]);
  });
}

function responseJson(data, code) {
  var output = ContentService.createTextOutput(JSON.stringify(data));
  output.setMimeType(ContentService.MimeType.JSON);
  return output;
}
