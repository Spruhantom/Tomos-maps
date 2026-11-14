# England routing data: controlled deployment plan

**Status: generated and CI-verified; not approved or published for public distribution.**

This plan covers the England-only Valhalla graph built by workflow run
[43638799674](https://github.com/Spruhantom/Tomos-maps/actions/runs/43638799674)
from commit `ce1111e4287a9b3083b5a06675ab94db37fdf902`.

## 1. What was inspected

The build log and workflow checks establish that the pipeline:

- downloaded the Geofabrik England PBF (1,714,105,943 bytes) and verified its
  provider MD5;
- downloaded the timezone-boundary archive and verified its pinned SHA-256;
- generated Valhalla 3.9.1 graph tiles, admin data and timezone data;
- wrote 791 routing tiles and a `tiles.tar` archive;
- passed a Liverpool-to-Manchester `auto` route smoke test (55.3 km);
- split the archive into five ordered parts, total 2,240,552,960 bytes;
- validated the manifest, each part's size/hash, and the reconstructed archive
  hash; and
- uploaded a 926,023,896-byte GitHub Actions artifact.

The artifact is a 30-day CI review artifact, not a stable production URL.
The single 926 MB review ZIP exceeded the available 512 MiB artifact-download
limit and could not be independently unpacked in this review. The pipeline is
now being changed to produce 450 MB maximum graph parts as separate Actions
artifacts, so each part can be independently downloaded and assembled. The build's own
manifest/part/whole-archive verification passed; a separate independent
consumer-side download and assembly test remains a release gate.

The artifact is retained by Actions until 14 December 2026 (subject to GitHub
retention settings). Do not use it as a long-term client download endpoint.

## 2. Licensing and attribution gate

The graph is derived from OpenStreetMap data. Geofabrik identifies its OSM
extracts as ODbL 1.0 and requires attribution; OSMF requires attribution and
share-alike for distributed OSM databases and derivative databases.

Required release treatment:

- Identify the OSM source and Geofabrik extract URL/date/checksum.
- Display **© OpenStreetMap contributors** and link to
  https://www.openstreetmap.org/copyright.
- Make the ODbL 1.0 licence and the applicable database/share-alike notice
  available alongside the data download.
- Preserve the timezone-boundary-builder data notice: its output data is ODbL
  (the builder's source code is MIT, which is a separate licence).
- Identify Valhalla 3.9.1 as the MIT-licensed routing engine used to produce
  the graph. The engine's software licence does not replace the data licence.
- Retain the exact Valhalla image digest used by the build in provenance.
- Keep software, generated databases, source datasets and configuration files
  separately identified in the asset inventory. Do not imply that one licence
  automatically covers every component.
- Have a human review the final notices and database classification before
  public distribution. This plan is an engineering checklist, not legal advice.

Sources:
- https://www.openstreetmap.org/copyright
- https://osmfoundation.org/wiki/Licence_and_Legal_FAQ
- https://download.geofabrik.de/technical.html
- https://www.geofabrik.de/de/data/download.html
- https://github.com/evansiroky/timezone-boundary-builder
- https://github.com/evansiroky/timezone-boundary-builder/blob/master/DATA_LICENSE
- https://github.com/valhalla/valhalla

## 3. Hosting design

Keep the already selected hybrid design:

1. **GitHub Pages** hosts only the small public catalogue and package metadata.
2. **GitHub Releases** hosts large, immutable, versioned routing assets.
3. The catalogue entry is added only after a release has passed all approval
   gates. An absent catalogue entry means the package is unavailable.
4. Never put multi-gigabyte data in Git history or in the Pages deployment.
5. Use a new version/tag for every graph refresh; never replace assets behind
   an existing version URL.
6. Keep the 30-day Actions artifact as a temporary review copy only.

Before selecting GitHub Releases for production, verify current per-asset and
repository storage limits, bandwidth/egress constraints, rate limits, and
download behaviour for large files. If these are unsuitable, evaluate an
object store/CDN later; do not silently switch providers or expose an
unapproved bucket.

## 4. Versioning and update policy

- Version the graph by source extract identity plus build recipe/engine version.
  Record source URL, retrieval timestamp, provider MD5, source SHA-256, byte
  size, Geofabrik replication state when available, Valhalla version and image
  digest, timezone dataset release/checksum, and builder commit.
- Build a new complete graph from a fresh, verified England extract for each
  release candidate. Do not apply raw OSM PBF diffs directly to the Valhalla
  graph archive.
- Keep the previous published graph available for rollback until the new
  version has passed the observation period.
- Publish a small signed catalogue update only after all asset checks pass.
  The client must pin a version, verify manifest schema, exact byte size and
  SHA-256 for every part, concatenate in manifest order, verify the whole
  archive hash, and install atomically.
- Failed or interrupted downloads must never replace the currently installed
  version. Keep the previous version until the new graph is fully validated.
- Define a rebuild cadence only after measuring source change frequency,
  build duration, storage cost and release demand. Do not trigger a full
  multi-gigabyte build on every unrelated commit.

## 5. Release gates

A future public release must not proceed until every item is checked:

- [ ] Independently download the review bundle and assemble the graph on a
      clean Linux machine using the documented assembler.
- [ ] Confirm the final graph hash and all asset hashes from the downloaded
      bytes, not only the build runner.
- [ ] Expand routing smoke tests to include multiple routes across England,
      ferries/tolls where relevant, motorways, urban streets and edge coverage.
- [ ] Record test coordinates, route distances, durations, engine version and
      expected failure cases; do not treat one Liverpool–Manchester route as
      full geographic validation.
- [ ] Check graph coverage at the England boundary and ensure no unintended
      cross-border gaps are represented as complete UK coverage.
- [ ] Complete component-by-component licence and attribution review.
- [ ] Add a human-readable `LICENSES`/notices bundle to the release assets.
- [ ] Confirm GitHub release asset limits, bandwidth and operational ownership.
- [ ] Generate and sign the catalogue; keep the signing private key outside
      Git and CI logs.
- [ ] Test catalogue signature, checksum failures, rollback and interrupted
      downloads with a standalone test client.
- [ ] Obtain explicit release approval before setting `publish_release=true`
      or adding the package to the public catalogue.

## 6. Application integration boundary

This deployment plan does not enable offline map downloads, offline routing,
offline search or any related app UI. The England graph is a data pipeline
pilot only. App integration requires a separate, explicitly approved task
after hosting, legal, package verification and device-level performance
testing are complete.


## 7. Review artifact transport improvement

The first successful build uploaded all files as one 926 MB Actions artifact.
That exceeds the available independent artifact-download path's 512 MiB limit,
so it could not be unpacked for a consumer-side verification in this review.

The builder now splits `tiles.tar` into parts of at most 450,000,000 bytes.
The workflow uploads each part as a separate 30-day Actions artifact and uploads
the manifest, metadata, admin database, timezone database and Valhalla config
as a separate small metadata artifact. The current England graph should produce
five parts; the workflow permits up to six and fails clearly if more are needed.

After the next successful build, independently download all part artifacts
and the metadata artifact, then run:

```bash
python3 tools/assemble_routing_graph.py \
  --manifest england-routing.manifest.json \
  --parts-dir ./downloaded-parts \
  --output tiles.tar
```

Compare the assembled SHA-256 and byte size with the manifest. This independent
test remains mandatory; splitting the artifacts only makes it possible, it
does not itself count as proof.

## 8. Publication workflow safeguards

The manual workflow now requires three separate confirmations before it can
publish a GitHub prerelease:

- `publish_release=true`;
- `licence_review_confirmed=true`; and
- `independent_validation_confirmed=true`.

All defaults are false. If either review confirmation is missing, the graph
can still be built and uploaded for review, but the release step is skipped.
These inputs are explicit human attestations; the workflow cannot determine
whether a legal review or independent validation was genuinely performed.
