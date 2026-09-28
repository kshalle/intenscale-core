# interpreter-perl — Perl 4.036

The scripting-interpreter workload: string and hash manipulation, regular
expressions, allocator traffic.

## What is vendored here

`perl-4.036/` — Perl 4.036, the final Perl 4 release.

- URL: https://www.cpan.org/src/unsupported/4.036/perl-4.036.tar.gz
- Tarball sha256: `f690888369d6297ce8f6c5d75c573d063c14e4ab03319ebc1a7e959787cfc76a`
- Upstream date: 1994-02-01
- Retrieved: 2026-09-07

## Licence

**GPL-1.0-or-later OR Artistic-1.0** (dual, recipient's choice) — Larry Wall's
standard Perl terms.  Texts are in `perl-4.036/Copying` (GPL v1) and
`perl-4.036/Artistic`.  `perl-4.036/README` states the dual option and Wall's
interpretation that scripts run by the interpreter are not derived works.
Taking the Artistic option avoids any GPL discussion entirely.

The distribution carries additional copyright notices from Diomidis
Spinellis, Tom Dinger, Jack Hudler and the Regents of the University of
California in its other-platform subtrees and comments.  These are
distributed by upstream under Perl's own terms and impose nothing further;
they are preserved because the tree is unmodified.

## Why this version

Perl 4 is a tree-walking interpreter whose hot paths are exactly the ones
worth measuring on an integer core: string building and comparison, hash
insertion and lookup, regex compilation and matching, and heavy malloc
traffic.  Perl 5 was considered and rejected — substantially larger, with no
additional workload character at this scale.

## Local modifications

None.  Tree is upstream as extracted.

## Notes for benchmark use

The bare-metal port is in `../baremetal/perl-perl4`.  Perl 4 expects a
`Configure`-generated `config.h`, which cannot be produced on a target with no
shell; the port derives one and records exactly what it changed.  Its
workloads (prime factorisation, anagram grouping) are written for this suite,
and are in the Perl 4 subset so they also run on a modern host perl — which is
how the port is verified.
