# Offline package release gates

Before signing or publishing any region, run `python3 tools/validate_offline_release.py --manifest <package.manifest.json> --archive <package.zip> --licence-evidence <review-record.json>`. The validator checks all required map, routing, routing-support and search roles; geographic bounds; exact ZIP membership; duplicate and symlink rejection; byte sizes; SHA-256 hashes; attribution; and a human-reviewed redistribution-permission record.

The evidence JSON must contain `redistributionPermitted: true`, `reviewedBy`, `reviewedAt` in YYYY-MM-DD form, a non-empty `sources` list (each with `name`, `licence`, and `evidence`), and `requiredAttribution`. Public availability of source data is not proof of redistribution rights. Review ODbL, derived data, postcode/address data, routing inputs, glyph/font assets and notices separately. Keep private agreements out of Git.

The validator does not sign or publish. Keep the Ed25519 private key in GitHub Actions secrets, pin only its public key in the app, sign the exact catalogue bytes, and publish immutable versioned assets over HTTPS. Do not configure production catalogue URLs until real assets have passed validation.

Android and Linux must each be tested for progress, pause, process restart, resume after network interruption, corrupted archive rejection, atomic replacement, independent layer deletion, and airplane-mode map rendering, postcode search and routing. CI builds are not physical-device tests.

Current state: the England Valhalla candidate passed route and checksum checks but is not a complete basemap or postcode index and has not been publicly released. Publisher secrets, signed production catalogue URLs, legal evidence and physical-device test records remain outstanding. Both download flags stay disabled until all gates pass.
