# Command-line interface

The Unix launcher is `bin/hazel`; the Windows launcher is `bin/hazel.ps1`.
The executable JAR uses the same arguments after `java -jar target/hazel.jar`.
An installed Clojure CLI uses `clojure -M:run`.

## Commands

```text
hazel help
hazel check STUDY.edn
hazel run STUDY.edn [--out DIRECTORY]
hazel inspect BUNDLE.edn
hazel replay BUNDLE.edn
hazel verify BUNDLE.edn
```

`check` validates the inert data shape, compiles the model, and reports structural
properties. It does not run the experiment or establish that every numerical
option is adequate for it.

`run` executes a complete study, writes a content-addressed EDN bundle, and
renders `index.html`. The default directory is `out/<study filename stem>`.
The printed EDN map contains the actual artifact and report paths. A later run
with identical result content uses the same bundle name.

`inspect` verifies payload and model hashes before showing metadata. It does
not recompute the numerical calculation.

`replay` verifies hashes, reruns the stored request, and compares the entire
canonical result. It is strict about floating-point digits. Evidence created
on a different numerical platform can require regeneration.

`verify` is specific to rational certificate studies. It independently rebuilds
the arithmetic recurrence and checks any stored event interval. It does not
dispatch the general floating-point solver.

## Exit classification

| Code | Meaning |
|---|---|
| 0 | Requested command completed with a successful method status |
| 1 | Invalid input, unsupported operation, I/O error, or failed verification |
| 2 | Calculation returned an explicit incomplete outcome |

A state-budget or optimizer-budget exhaustion is not reported as convergence.
Incomplete numerical output is still saved when the experiment returns it.
Exceptions that prevent a result are printed to stderr as a concise EDN
diagnostic and return code one.

## Full project workflows

```sh
make test
make examples
make controls
make acceptance
make verify
make jar
```

The Windows equivalents use `bin/clj.ps1 -m hazel.test-runner`,
`bin/clj.ps1 -m hazel.acceptance <command>`, and `bin/clj.ps1 -m hazel.build`.

Acceptance writes raw test output, negative-control observations, example
bundles, reports, numerical measurements, package subprocess logs, and the
tested standalone JAR under `out/acceptance/`.

`make verify` requires that evidence to exist and match current source. A source
change makes old evidence stale. Run acceptance again instead of editing its
status fields. Narrative documentation is excluded from the source fingerprint;
implementation, test, resource, example, launcher, and build bytes are included.

## Runtime bootstrap

The source launchers fetch three artifacts from Maven Central and verify their
SHA-256 hashes on every launch. A corrupt cache fails with a checksum diagnostic.
Remove only the reported cached JAR and rerun to fetch it again.

An offline machine can use an already populated `.cache/runtime/`, a standard
Clojure installation with resolved dependencies, or a previously built executable
JAR. The first source launch requires network access for its pinned runtime.
