(ns hazel.cli
  "CLI boundary: data errors become concise diagnostics and stable exit codes."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hazel.io.edn :as edn]
            [hazel.io.bundle :as bundle]
            [hazel.io.report :as report]
            [hazel.model.network :as net]
            [hazel.model.structure :as structure]
            [hazel.experiment :as experiment]
            [hazel.math.numeric :as n]))

(def usage
  (str "Hazel 0.1.0\n\n"
       "  hazel check STUDY.edn             Validate model and experiment schema\n"
       "  hazel run STUDY.edn [--out DIR]   Run, save a bundle, and render index.html\n"
       "  hazel replay BUNDLE.edn           Verify content and strictly recompute\n"
       "  hazel inspect BUNDLE.edn          Verify content and show a compact summary\n"
       "  hazel help                       Show this text\n\n"
       "Exit codes: 0 success, 1 invalid input or failed verification, 2 incomplete calculation.\n"))

(def success-statuses #{:complete :converged :valid :reproduced :evaluated :ok})

(defn- parse-options [args]
  (loop [args args options {}]
    (if (empty? args) options
        (let [[option value & more] args]
          (when-not (and (= option "--out") value (not (str/starts-with? value "--"))
                         (not (contains? options :out)))
            (n/fail "Expected one optional --out DIRECTORY" {:arguments args}))
          (recur more (assoc options :out value))))))

(defn replay! [path]
  (let [{:keys [model request result digest]} (bundle/read! path)
        actual (assoc (experiment/run model request)
                      :title (:title result) :species (:species model)
                      :model-hash (:hash model) :structure (structure/topology model))
        expected-hash (edn/sha256 (bundle/portable result))
        actual-hash (edn/sha256 (bundle/portable actual))]
    (when-not (= expected-hash actual-hash)
      (n/fail "Strict numerical replay differs from stored result"
              {:expected expected-hash :actual actual-hash}))
    {:status :reproduced :digest digest :result-hash actual-hash}))

(defn dispatch [args]
  (let [[command path & options] args]
    (case command
      (nil "help" "--help" "-h") {:exit 0 :text usage}
      "check" (do (when (or (nil? path) (seq options)) (n/fail "check needs one study path" {}))
                  (let [study (experiment/validate-study (edn/read-file path))
                        model (net/compile-model (:model study))]
                    {:exit 0 :data {:status :valid :model-hash (:hash model)
                                    :structure (structure/topology model)}}))
      "run" (do (when (nil? path) (n/fail "run needs a study path" {}))
                (let [options (parse-options options)
                      directory (or (:out options) (str "out/" (str/replace (.getName (io/file path)) #"\.edn$" "")))
                      {:keys [model request result]} (experiment/run-study (edn/read-file path))
                      artifact (bundle/create! directory model request result)
                      report-path (report/write! (io/file directory "index.html") model request result (:digest artifact))]
                  {:exit (if (success-statuses (:status result)) 0 2)
                   :data {:status (:status result) :bundle (:path artifact) :report report-path
                          :digest (:digest artifact)}}))
      "inspect" (do (when (or (nil? path) (seq options)) (n/fail "inspect needs one bundle path" {}))
                    (let [{:keys [digest model request result]} (bundle/read! path)]
                      {:exit 0 :data {:status (:status result) :digest digest :model (:name model)
                                     :kind (:kind request) :species (:species model)
                                     :projected-states (count (:states result))}}))
      "replay" (do (when (or (nil? path) (seq options)) (n/fail "replay needs one bundle path" {}))
                   {:exit 0 :data (replay! path)})
      (n/fail "Unknown command; use hazel help" {:command command}))))

(defn invoke [args]
  (try (dispatch args)
       (catch Exception e {:exit 1 :error (.getMessage e)
                           :details (dissoc (ex-data e) :hazel/error)})))

(defn -main [& args]
  (let [{:keys [exit text data error details]} (invoke args)]
    (if error
      (binding [*out* *err*] (println (pr-str {:error error :details details})))
      (if text (print text) (println (pr-str data))))
    (flush)
    (shutdown-agents)
    (System/exit exit)))
