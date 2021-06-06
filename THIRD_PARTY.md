# Third-party notices

Hazel source is MIT licensed. The executable JAR includes the following
independently licensed runtime components from Maven Central:

| Artifact | Version | License |
|---|---|---|
| `org.clojure/clojure` | 1.12.6 | Eclipse Public License 1.0 |
| `org.clojure/spec.alpha` | 0.5.238 | Eclipse Public License 1.0 |
| `org.clojure/core.specs.alpha` | 0.4.74 | Eclipse Public License 1.0 |

The packager retains license, notice, and copyright entries under
`META-INF/licenses/<artifact filename>/` to avoid name collisions. Runtime
artifacts can contain their own additional notices; inspect those retained
files for the complete distribution terms.

Official project and license sources:

* [Clojure](https://clojure.org/)
* [Clojure license](https://github.com/clojure/clojure/blob/master/epl-v10.html)
* [spec.alpha](https://github.com/clojure/spec.alpha)
* [core.specs.alpha](https://github.com/clojure/core.specs.alpha)

No numerical solver, native scientific library, plotting framework, web
framework, or external inference engine is bundled. The JDK is provided by the
user or CI environment and is not redistributed in Hazel's JAR.
