# PandaScore contract fixtures

These are synthetic **test-only** fixtures following the documented PandaScore
REST match, opponent, result, tournament, series and league structure. IDs,
dates, matches, scores and image paths are deliberately illustrative; they are
not evidence that these matches occurred or these images exist. No fixture is
loaded by production code.

References:
- https://developers.pandascore.co/reference/get_matches_matchidorslug
- https://developers.pandascore.co/reference/get_csgo_matches_upcoming
- https://developers.pandascore.co/reference/get_csgo_matches_running
- https://developers.pandascore.co/docs/getting-started

The running/final results intentionally reverse `team_id` ordering to catch
score attribution bugs. The scheduled time includes an offset to verify UTC
normalization. Unknown fields model forward-compatible payload evolution.
`complete=false` concerns post-game detailed statistics, not the fixture's
final result; settlement uses only the match result and never those statistics.
