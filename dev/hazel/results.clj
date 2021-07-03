(ns hazel.results
  "Generate public measurements only from evidence that verifies for current source."
  (:require [clojure.java.io :as io]
            [hazel.acceptance :as acceptance]
            [hazel.io.edn :as edn]
            [hazel.io.bundle :as bundle]))

(defn- f [x] (String/format java.util.Locale/ROOT "%.6g" (to-array [(double x)])))

(defn write! [directory destination]
  (acceptance/verify! directory)
  (let [evidence (edn/read-file (io/file directory "acceptance.edn") (* 64 1024 1024))
        example (fn [name]
                  (let [entry (first (filter #(= (str "examples/" name ".edn") (:study %)) (:examples evidence)))]
                    (:result (bundle/read! (io/file directory "examples" (:bundle entry))))))
        certificate (example "certificate") telegraph (example "telegraph")
        fitted (example "rate-fit") rare (example "rare-arrivals")
        tests (:tests evidence) package (:package evidence)
        text (str "# Observed results\n\n"
                  "This file is generated from a source-bound local acceptance campaign. "
                  "It reports executed work on the environment below. Hosted matrix status is "
                  "reported by the workflow; this local record does not stand in for those jobs.\n\n"
                  "| Environment | Observed value |\n|---|---|\n"
                  "| JDK | `" (:java-version evidence) "` |\n"
                  "| OS / architecture | " (:os evidence) " / " (:architecture evidence) " |\n"
                  "| Campaign elapsed time | " (f (:elapsed-seconds evidence)) " seconds |\n"
                  "| Source fingerprint | `" (:source-fingerprint evidence) "` |\n\n"
                  "## Executed acceptance\n\n"
                  "| Check | Observed result |\n|---|---|\n"
                  "| Test cases | " (:test tests) " |\n"
                  "| Assertions | " (:pass tests) " passed, " (:fail tests) " failed, " (:error tests) " errors |\n"
                  "| Mutation controls | " (get-in evidence [:negative-controls :detected]) " / " (get-in evidence [:negative-controls :executed]) " detected |\n"
                  "| Complete supplied studies | " (count (:examples evidence)) " executed and strictly replayed |\n"
                  "| Rational certificate | Independently checked from source reactions |\n"
                  "| Fresh executable builds | " (:fresh-process-builds package) " identical on this runtime |\n"
                  "| Package outside checkout | Study, report, certificate verification, and replay passed |\n"
                  "| Tested package bytes | " (:bytes package) " |\n"
                  "| Tested package SHA-256 | `" (:sha256 package) "` |\n\n"
                  "## Numerical fixtures\n\n"
                  "The arrival certificate encloses `P(X(1)>=3)` for rate two. Its exact "
                  "fractions are retained in the bundle. Decimal presentation below is only a display.\n\n"
                  "| Fixture | Observed value |\n|---|---|\n"
                  "| Certificate lower display | " (f (get-in certificate [:event-interval :lower])) " |\n"
                  "| Certificate upper display | " (f (get-in certificate [:event-interval :upper])) " |\n"
                  "| Exact interval width, decimal display | " (f (- (get-in certificate [:event-interval :upper]) (get-in certificate [:event-interval :lower]))) " |\n"
                  "| Promoter final projected populations | " (count (:states telegraph)) " |\n"
                  "| Promoter refinement attempts | " (count (:history telegraph)) " |\n"
                  "| Promoter escaped probability | " (f (:escape-probability telegraph)) " |\n"
                  "| Promoter series tail allowance | " (f (:series-tail-bound telegraph)) " |\n"
                  "| Arrival-rate maximum likelihood estimate | " (f (get-in fitted [:parameters :arrival])) " |\n"
                  "| Tilted rare-arrival probability estimate | " (f (:estimate rare)) " |\n"
                  "| Tilted estimate empirical standard error | " (f (:standard-error rare)) " |\n\n"
                  "The fit's analytic maximum is 3.5. The statistical estimate is not an "
                  "exact probability or a deterministic confidence interval. Binary64 "
                  "projection diagnostics do not inherit rational certificate status.\n\n"
                  "## Descriptive measurements\n\n"
                  "Three warmup invocations precede five measured invocations. All raw "
                  "timings and result hashes are retained. Representations are prepared "
                  "outside each product loop. These measurements are not isolated-process "
                  "JMH results or comparisons with optimized numerical libraries.\n\n"
                  "| Workload | Iterations per invocation | Median seconds | Median ns per iteration |\n|---|---:|---:|---:|\n"
                  (apply str (for [case (get-in evidence [:benchmarks :cases])]
                               (str "| " (name (:name case)) " | " (:iterations case) " | "
                                    (f (:median-seconds case)) " | " (f (:median-nanoseconds-per-iteration case)) " |\n")))
                  "\nThe dense case is the included persistent-data reference. The sparse case "
                  "uses CSR arrays. Their difference describes these implementations and "
                  "this small workload, not a general hardware or language speed claim.\n\n"
                  "## Reproduce\n\n```sh\nmake acceptance verify\nbin/clj -m hazel.results\n```\n\n"
                  "Raw evidence is in `out/acceptance/`, which is intentionally not tracked. "
                  "CI retains environment-specific bundles as workflow artifacts. "
                  "See [verification](VERIFICATION.md) and [limits](LIMITS.md) for the acceptance boundary.\n")]
    (bundle/atomic-write! destination text)
    {:status :written :path (str destination) :source-fingerprint (:source-fingerprint evidence)}))

(defn -main [& [directory destination]]
  (println (pr-str (write! (or directory acceptance/default-directory) (or destination "docs/RESULTS.md")))))
