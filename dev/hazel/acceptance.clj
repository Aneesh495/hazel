(ns hazel.acceptance
  "Source-frozen acceptance campaign and independent artifact verification."
  (:refer-clojure :exclude [run!])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test]
            [hazel.source :as source]
            [hazel.test-runner :as tests]
            [hazel.controls :as controls]
            [hazel.benchmark :as benchmark]
            [hazel.package-check :as package]
            [hazel.cli :as cli]
            [hazel.io.edn :as edn]
            [hazel.io.bundle :as bundle]
            [hazel.math.numeric :as n]))

(def default-directory "out/acceptance")

(defn- relative [root path]
  (str/replace (str (.relativize (.toPath (.getCanonicalFile (io/file root)))
                                (.toPath (.getCanonicalFile (io/file path))))) "\\" "/"))

(defn- ensure! [condition message details]
  (when-not condition (n/fail message details)))

(defn examples!
  [directory]
  (mapv
   (fn [file]
     (let [name (str/replace (.getName file) #"\.edn$" "")
           out (io/file directory name)
           result (cli/invoke ["run" (str file) "--out" (str out)])
           _ (ensure! (zero? (:exit result)) "Example execution failed" {:example (str file) :result result})
           path (get-in result [:data :bundle])
           replay (cli/invoke ["replay" path])
           _ (ensure! (zero? (:exit replay)) "Example replay failed" {:example (str file) :result replay})
           object (bundle/read! path)
           verified (when (= :certify (get-in object [:request :kind])) (cli/invoke ["verify" path]))]
       (when verified (ensure! (zero? (:exit verified)) "Independent certificate failed" {:result verified}))
       {:study (str file) :status (get-in result [:data :status])
        :bundle (relative directory path) :digest (:digest object)
        :strict-replay? true :independent-certificate? (boolean verified)}))
   (sort-by str (filter #(and (.isFile %) (.endsWith (.getName %) ".edn")) (file-seq (io/file "examples"))))))

(defn- artifact-manifest [directory]
  (into (sorted-map)
        (for [file (file-seq (io/file directory)) :when (.isFile file)
              :let [path (relative directory file)]
              :when (not (contains? #{"acceptance.edn"} path))]
          [path {:bytes (.length file) :sha256 (edn/sha256-bytes (java.nio.file.Files/readAllBytes (.toPath file)))}])))

(defn- clear-directory! [directory]
  (let [file (.getCanonicalFile (io/file directory))
        out (.getCanonicalFile (io/file "out"))]
    (ensure! (.startsWith (.toPath file) (.toPath out)) "Evidence directory must be under out/" {:directory directory})
    (ensure! (not= file out) "Evidence directory cannot be out/ itself" {})
    (when (.exists file) (doseq [child (reverse (file-seq file))] (io/delete-file child)))
    (.mkdirs file)))

(defn run!
  ([] (run! default-directory))
  ([directory]
   (clear-directory! directory)
   (let [before (source/manifest) fingerprint (edn/sha256 before)
         start (System/nanoTime)
         _ (println "Executing all tests against frozen source" fingerprint)
         test-result (with-open [writer (io/writer (io/file directory "tests.log"))]
                       (binding [test/*test-out* writer] (tests/run!)))
         _ (ensure! (and (pos? (:test test-result)) (zero? (+ (:fail test-result) (:error test-result))))
                    "Test campaign failed" test-result)
         _ (println "Executing negative controls")
         controls (controls/run! (io/file directory "controls"))
         _ (ensure! (= :passed (:status controls)) "Negative controls did not detect every mutation" controls)
         _ (println "Executing studies, strict replay, and independent certificates")
         examples (examples! (io/file directory "examples"))
         _ (ensure! (seq examples) "No examples discovered" {})
         _ (println "Measuring actual numerical workloads")
         benchmarks (benchmark/run!)
         _ (bundle/atomic-write! (io/file directory "benchmarks.edn") (edn/canonical benchmarks))
         _ (println "Checking standalone packaging from an unrelated directory")
         package (package/run! (io/file directory "package"))
         _ (ensure! (= before (source/manifest)) "Source changed during acceptance" {})
         result {:format :hazel/acceptance-v1 :status :passed
                 :source-fingerprint fingerprint :source-manifest before
                 :java-version (System/getProperty "java.runtime.version")
                 :os (System/getProperty "os.name") :architecture (System/getProperty "os.arch")
                 :elapsed-seconds (/ (- (System/nanoTime) start) 1e9)
                 :tests test-result :negative-controls controls :examples examples
                 :benchmarks benchmarks :package package :artifacts (artifact-manifest directory)}]
     (bundle/atomic-write! (io/file directory "acceptance.edn") (edn/canonical result))
     (select-keys result [:status :source-fingerprint :elapsed-seconds :tests :package]))))

(defn verify!
  ([] (verify! default-directory))
  ([directory]
   (let [path (io/file directory "acceptance.edn") result (edn/read-file path (* 64 1024 1024))]
     (ensure! (and (= :hazel/acceptance-v1 (:format result)) (= :passed (:status result)))
              "Acceptance status or format is invalid" {})
     (ensure! (= (:source-fingerprint result) (source/fingerprint)) "Acceptance evidence is stale for current source" {})
     (ensure! (= (:source-manifest result) (source/manifest)) "Source manifest mismatch" {})
     (ensure! (= (:artifacts result) (artifact-manifest directory)) "Acceptance artifact bytes differ" {})
     (ensure! (and (pos? (get-in result [:tests :test]))
                   (zero? (+ (get-in result [:tests :fail]) (get-in result [:tests :error])))) "Recorded tests failed" {})
     (ensure! (and (= :passed (get-in result [:negative-controls :status]))
                   (= (get-in result [:negative-controls :executed]) (get-in result [:negative-controls :detected]))
                   (every? :detected? (get-in result [:negative-controls :controls]))) "Control evidence is incomplete" {})
     (doseq [example (:examples result)]
       (let [bundle-path (io/file directory "examples" (:bundle example))
             replay (cli/invoke ["replay" (str bundle-path)])]
         (ensure! (zero? (:exit replay)) "Stored example fails numerical replay" {:example example :replay replay})
         (when (:independent-certificate? example)
           (ensure! (zero? (:exit (cli/invoke ["verify" (str bundle-path)]))) "Stored certificate failed" {:example example}))))
     {:status :verified :source-fingerprint (:source-fingerprint result)
      :artifacts (count (:artifacts result)) :examples (count (:examples result))
      :negative-controls (get-in result [:negative-controls :detected])})))

(defn -main [& [command directory]]
  (let [directory (or directory default-directory)]
    (try
      (println (pr-str (case command
                        "run" (run! directory)
                        "verify" (verify! directory)
                        "examples" (examples! "out/examples")
                        "controls" (controls/run! "out/controls")
                        (n/fail "Expected run, verify, examples, or controls" {}))))
      (shutdown-agents)
      (catch Exception e
        (binding [*out* *err*] (println (pr-str {:status :failed :message (.getMessage e) :details (ex-data e)})))
        (shutdown-agents)
        (System/exit 1)))))
