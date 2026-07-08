# Native Libraries — Setup Guide

This directory must contain the following JARs/AARs **before I2P outproxy
support is enabled**. Without them the app still builds and runs — Open Food
Facts calls just go direct (BuildConfig.I2P_OUTPROXY_ENABLED = false).

They are excluded from version control because they are large binary
artifacts that must be built from source or obtained from known-good build
artefacts. This is the exact same set of files used by buzzr-p2p/verzus-p2p —
if you already built them for one of those projects, just copy the three
files here instead of rebuilding.

---

## Required files

| File | Source project | Build step |
|------|----------------|------------|
| `router.jar` | [i2p.i2p](https://github.com/i2p/i2p.i2p) | `ant pkg` |
| `sam.jar` | [i2p.i2p](https://github.com/i2p/i2p.i2p) | `ant pkg` |
| `i2p-android-client.aar` | [i2p.android.base](https://github.com/i2p/i2p.android.base) | `./gradlew assembleRelease` |

---

## Step-by-step build instructions

### 1. Build the I2P core JARs (`router.jar`, `sam.jar`)

```bash
# Prerequisites: Java 8+, Apache Ant
git clone https://github.com/i2p/i2p.i2p.git ~/git/i2p.i2p
cd ~/git/i2p.i2p
ant pkg
# After a successful build the JARs are in ~/git/i2p.i2p/pkg/
cp ~/git/i2p.i2p/pkg/router.jar  <repo>/app/libs/
cp ~/git/i2p.i2p/pkg/sam.jar     <repo>/app/libs/
```

### 2. Build the Android AAR (`i2p-android-client.aar`)

```bash
# Prerequisites: Android SDK (ANDROID_HOME set), Java 11+
git clone https://github.com/i2p/i2p.android.base.git ~/git/i2p.android.base
cd ~/git/i2p.android.base
./gradlew assembleRelease
# Output AAR is usually under client/build/outputs/aar/
cp ~/git/i2p.android.base/client/build/outputs/aar/client-release.aar \
   <repo>/app/libs/i2p-android-client.aar
```

### 3. Or just copy from an existing techducat checkout

```bash
cp ~/git/buzzr-p2p/app/libs/{router.jar,sam.jar,i2p-android-client.aar} <repo>/app/libs/
```

---

## I2P reseed / router certificates

`EmbeddedI2PRouter.copyI2PCertsFromAssets()` expects reseed/router/SSL
certificates under `app/src/main/assets/i2p/certificates/{reseed,router,ssl}/`.
These are NOT bundled in this repo snapshot — copy them from
`i2p.i2p`'s `installer/resources/certificates/` directory (or from an existing
techducat app's assets/ if you've already set one up), matching the same
`reseed` / `router` / `ssl` subfolder layout.

---

## Outproxy destination

`I2POutproxyTunnel` defaults to `false.i2p`. This is a volunteer-run outproxy
that itself relays clearnet requests through Tor — see the kdoc in
`I2POutproxyTunnel.kt` and `EmbeddedI2PRouter.kt` for the tradeoffs. No API key
or account is needed for it since Open Food Facts itself requires none.
