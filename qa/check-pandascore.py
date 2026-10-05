"""Explicit manual verification only. Never run by CI; never prints credentials or provider error bodies."""
import argparse
import collections
import datetime as dt
import json
import os
from pathlib import Path
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def private_config(path):
    values = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                if key.strip() in {"PANDASCORE_API_TOKEN", "PANDASCORE_VIDEOGAMES"}:
                    values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path(".env"))
    args = parser.parse_args()
    config = private_config(args.env_file)
    token = os.environ.get("PANDASCORE_API_TOKEN", config.get("PANDASCORE_API_TOKEN", "")).strip()
    games = os.environ.get("PANDASCORE_VIDEOGAMES", config.get("PANDASCORE_VIDEOGAMES", "csgo,lol,valorant")).split(",")
    games = list(dict.fromkeys(game.strip() for game in games))
    if not games or any(game not in {"csgo", "lol", "valorant"} for game in games):
        print(json.dumps({"status": "INVALID_CONFIGURATION", "requestsPerformed": 0}))
        return 2
    report = {"configured": bool(token), "verifiedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "requestsPerformed": 0, "responses": []}
    if not token or token in {"YOUR_TOKEN_HERE", "changeme", "fake"}:
        report.update(status="UNCONFIGURED", authenticated=False, httpStatus=None)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 2
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context()))

    def safe(value):
        return str(value).replace(token, "[REDACTED]")[:180] if value is not None else None

    for game in games:
        for feed in ("running", "upcoming", "past"):
            endpoint = f"https://api.pandascore.co/{game}/matches/{feed}"
            observed = []
            for page in range(1, 4):
                query = urllib.parse.urlencode({"per_page": 100, "page": page, "sort": "id"})
                request = urllib.request.Request(endpoint + "?" + query, headers={"Authorization": "Bearer " + token, "Accept": "application/json"})
                item = {"endpoint": endpoint, "page": page}
                report["requestsPerformed"] += 1
                try:
                    with opener.open(request, timeout=12) as response:
                        item.update(httpStatus=response.status, remainingRequests=safe(response.headers.get("X-Rate-Limit-Remaining")))
                        body = response.read(8_000_001)
                        if len(body) > 8_000_000:
                            raise ValueError("response too large")
                        payload = json.loads(body)
                        if not isinstance(payload, list) or any(not isinstance(match, dict) for match in payload):
                            raise ValueError("invalid list")
                        observed.extend(payload)
                        item["returnedCount"] = len(payload)
                        report["responses"].append(item)
                        if len(payload) < 100:
                            break
                except urllib.error.HTTPError as error:
                    item.update(httpStatus=error.code, retryAfter=safe(error.headers.get("Retry-After")))
                    report["responses"].append(item)
                    report.update(status="RATE_LIMITED" if error.code == 429 else "ERROR", authenticated=False if error.code in (401, 403) else None)
                    print(json.dumps(report, ensure_ascii=False, indent=2))
                    return 1
                except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
                    item["errorType"] = type(error).__name__
                    report["responses"].append(item)
                    report["status"] = "ERROR"
                    print(json.dumps(report, ensure_ascii=False, indent=2))
                    return 1
            report.setdefault("feeds", []).append({
                "game": game, "feed": feed, "returnedCount": len(observed), "paginationCapped": len(observed) == 300,
                "externalStatuses": dict(collections.Counter(safe(match.get("status")) for match in observed)),
                "samples": [{"id": match.get("id"), "name": safe(match.get("name")), "status": safe(match.get("status")),
                             "scheduledAt": safe(match.get("scheduled_at")), "endedAt": safe(match.get("end_at")),
                             "championship": safe((match.get("tournament") or {}).get("name")),
                             "opponents": [safe((opponent.get("opponent") or {}).get("name")) for opponent in (match.get("opponents") or [])],
                             "results": [{"teamId": result.get("team_id") if isinstance(result.get("team_id"), int) else None,
                                          "score": result.get("score") if isinstance(result.get("score"), int) else None}
                                         for result in (match.get("results") or []) if isinstance(result, dict)]} for match in observed[:3]],
            })
    report.update(status="VERIFIED", authenticated=True)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
