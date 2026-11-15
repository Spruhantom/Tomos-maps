# Offline package release gates

Before signing or publishing any region, run `python3 tools/validate_offline_release.py --manifest <package.manifest.json> --archive <package.zip> --licence-evidence <review-record.json>`. The validator checks all required map, routing, routing-support and search roles; geographic bounds; exact ZIP membership; duplicate and symlink rejection; byte sizes; SHA-256 hashes; attribution; and a human-reviewed redistribution-permission record.

The evidence JSON must contain `redistributionPermitted: true`, `reviewedBy`, `reviewedAt` in YYYY-MM-DD form, a non-empty `sources` list (each with `name`, `licence`, and `evidence`), and `requiredAttribution`. Public availability of source data is not proof of redistribution rights. Review ODbL, derived data, postcode/address data, routing inputs, glyph/font assets and notices separately. Keep private agreements out of Git.

The validator does not sign or publish. Keep the Ed25519 private key in GitHub Actions secrets, pin only its public key in the app, sign the exact catalogue bytes, and publish immutable versioned assets over HTTPS. Do not configure production catalogue URLs until real assets have passed validation.

Android and Linux must each be tested for progress, pause, process restart, resume after network interruption, corrupted archive rejection, atomic replacement, independent layer deletion, and airplane-mode map rendering, postcode search and routing. CI builds are not physical-device tests.

Current state: the England Valhalla candidate passed route and checksum checks but is not a complete basemap or postcode index and has not been publicly released. Publisher secrets, signed production catalogue URLs, legal evidence and physical-device test records remain outstanding. Both download flags stay disabled until all gates pass.


## Liverpool three-layer build

The combined Liverpool builder now feeds the independent download format:

```sh
OFFLINE_LICENCE_EVIDENCE=/secure/review/liverpool-licences.json \
PUBLISHER_PRIVATE_KEY=/secure/keys/tomos-ed25519.pem \
LAYER_PACKAGE_BASE_URL=https://downloads.example.invalid/tomos/maps/liverpool/ \
PACKAGE_VERSION=2026.11.15 \
bash tools/build-liverpool-offline-layers.sh
```

Replace the example host and paths with real publisher infrastructure. The script builds the regional basemap, Valhalla graph/support files and postcode index, verifies the combined archive, splits it into MAP/ROUTING/SEARCH ZIPs, runs the licence and checksum preflight on each, and signs a catalogue. It does not upload files or change app flags. The URL in the signed catalogue must match the location where the immutable ZIP and manifest files are actually hosted.

The MAP package includes its MBTiles and glyph directory. The ROUTING package includes tiles.tar, admins.sqlite, timezones.sqlite and valhalla.json. The SEARCH package includes the region's offline postcode index. All three use the same area ID and coverage bounds so the app can combine them safely.


## First-time publisher key provisioning

Generate a key pair on a trusted publisher machine:

```sh
bash tools/generate-publisher-key.sh
```

The script refuses to overwrite an existing key, stores the private PEM with owner-only permissions, and prints the base64 DER public key. Pin that public key in `RegionalPackageBuildConfig.PINNED_ED25519_PUBLIC_KEY_BASE64`, and store the private PEM in a protected release secret/environment. The release pipeline derives the public key from the supplied private key and refuses to sign if it does not match the app pin. Never send the private key in chat, commit it, or put it in an app build.


## Verified source-specific licensing blockers

The public terms checked for this pipeline establish:
- OpenStreetMap data is ODbL 1.0; derived databases may carry share-alike obligations and must retain attribution.
- OS Code-Point Open (GB) is OS OpenData under the Open Government Licence. OS requires OS, Royal Mail and National Statistics copyright statements in attribution.
- ONS postcode products derived from Code-Point Open have a special Northern Ireland condition: BT postcode data requires separate LPS licensing for commercial use, and ONS says its NI end-user licence is for internal business use. Do not treat the ordinary GB/OGL licence as permission to publish BT records in a public app. Obtain explicit written LPS rights for the intended redistribution or exclude BT records and do not label the index UK-wide.
- Noto Sans glyphs are under SIL OFL 1.1. The build now bundles the upstream OFL text and copyright notice with the glyph files.

The release validator checks OSM attribution, the OS/Royal Mail/National Statistics attribution for OS/ONSPD postcode assets, the Noto OFL notice when glyphs are packaged, and LPS evidence whenever BT records are found. These checks do not replace review of the exact source agreements or legal advice.
