(ns hazel.test-runner
  "Discovers every test namespace from source declarations. Fails on empty runs."
  (:refer-clojure :exclude [run!])
  (:require [clojure.java.io :as io]
            [clojure.test :as t]))

(defn test-namespaces []
  (->> (file-seq (io/file "test"))
       (filter #(and (.isFile %) (.endsWith (.getName %) "_test.clj")))
       (map (fn [file]
              (with-open [reader (java.io.PushbackReader. (io/reader file))]
                (binding [*read-eval* false]
                  (let [form (read reader)]
                    (when-not (= 'ns (first form))
                      (throw (ex-info "Test file must begin with ns" {:file (str file)})))
                    (second form))))))
       sort vec))

(defn run! []
  (let [namespaces (test-namespaces)]
    (when (empty? namespaces)
      (throw (ex-info "No test namespaces discovered" {})))
    (doseq [n namespaces] (require n))
    (apply t/run-tests namespaces)))

(defn -main [& _]
  (let [{:keys [fail error test]} (run!)]
    (shutdown-agents)
    (System/exit (if (and (pos? test) (zero? (+ fail error))) 0 1))))
