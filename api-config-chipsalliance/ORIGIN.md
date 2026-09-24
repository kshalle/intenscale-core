# api-config-chipsalliance — rocketchip-config library

## What is vendored here

The `chipsalliance.rocketchip` config API library (the `Config`/`Field`
parameterization system Rocket Chip's `Parameters` mechanism is built on).

- URL: `https://github.com/chipsalliance/api-config-chipsalliance` (identity confirmed from package name `chipsalliance.rocketchip`, not from a README — none exists)
- Version: **not recorded**
- Commit: **not recorded**
- Retrieved: unknown

## Licence

**Apache-2.0.** Full text: `LICENSE` — genuine, unmodified standard Apache
License 2.0 text.

Per-file headers are the full standard Apache-2.0 block, e.g.:
`// Copyright 2016-2019 SiFive, Inc.` followed by the standard
"Licensed under the Apache License, Version 2.0..." notice. Confirmed present
on sampled files, including `design/craft/src/config/Config.scala`.

## Local modifications

Not verified — no commit is recorded to diff against.

## Notes for future audit work

Has its own `.github/workflows/test.yml` and build-system adapter files
(`build-rules/sbt/build.sbt`, `build-rules/mill/build.sc`), confirming a
genuine upstream GitHub checkout, but nothing in the tree pins an exact
commit or tag.
