# Offline map release device acceptance

CI emulator and desktop tests are necessary but do not replace testing on a
physical Android phone and a supported Linux installation. Do not enable
offline downloads until this checklist has been completed against the exact
signed catalogue and package hashes intended for release.

## Test build identity

Record the app commit, app version, catalogue SHA-256, catalogue signature
verification result, and MAP/ROUTING/SEARCH package IDs, versions, archive
sizes and SHA-256 values. Record the test device model and OS version. Do not
include personal location history or real household addresses in test logs.

## Android physical device

- [ ] Install the release-candidate APK cleanly and confirm online maps and
  foreground voice navigation still work.
- [ ] Refresh the signed catalogue over HTTPS and verify the signature using
  the app-pinned Ed25519 public key.
- [ ] Download MAP, ROUTING and SEARCH individually over Wi-Fi; compare
  downloaded and installed sizes with the catalogue.
- [ ] Pause each transfer, resume it, and verify byte-range continuation
  rather than restarting from zero.
- [ ] Interrupt network connectivity during each transfer; restore the
  connection and verify automatic or user-triggered recovery.
- [ ] Force-stop and relaunch the app during a partial transfer; verify
  progress state and partial bytes are recovered safely.
- [ ] Reboot the device with an installed region and verify all three layers
  remain available.
- [ ] Enable airplane mode. Confirm the regional map renders, postcode search
  returns local results, and an offline route can be calculated without
  network requests.
- [ ] Remove each layer independently and verify unrelated layers remain
  installed and the map does not claim complete offline coverage.
- [ ] Reinstall removed layers and repeat offline checks.
- [ ] Confirm storage-full and corrupt-package failures leave no partially
  installed package and do not damage online navigation.

## Linux physical installation

- [ ] Install the release-candidate package on a supported Linux machine.
- [ ] Repeat catalogue signature, individual downloads, pause/resume,
  interruption, process restart and recovery checks.
- [ ] Verify offline map rendering, postcode lookup and local routing in
  airplane mode or with network interfaces disabled.
- [ ] Verify independent layer deletion and reinstall.
- [ ] Confirm a corrupt archive, wrong checksum, expired/invalid signature or
  insufficient disk space fails safely and preserves existing installed data.

## Sign-off

Attach test logs with secrets and personal data removed. Record each item as
PASS, FAIL or NOT TESTED with a short note. Any FAIL or NOT TESTED item blocks
release activation. CI emulator/desktop results must be linked separately;
they must not be reported as physical-device sign-off.
