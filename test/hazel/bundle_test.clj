(ns hazel.bundle-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [hazel.fixtures :as f]
            [hazel.io.bundle :as bundle]
            [hazel.io.edn :as edn]))

(defn with-directory [f]
  (let [dir (.toFile (java.nio.file.Files/createTempDirectory "hazel-tests-" (make-array java.nio.file.attribute.FileAttribute 0)))]
    (try (f dir) (finally (doseq [file (reverse (file-seq dir))] (.delete file))))))

(deftest content-addressed-roundtrip
  (with-directory
    (fn [dir]
      (let [result {:probabilities [1.0 0.0] :log-weights [0.0 ##-Inf]}
            a (bundle/create! dir f/birth {:kind :test} result)
            b (bundle/create! dir f/birth {:kind :test} result)]
        (is (= a b))
        (is (= result (:result (bundle/read! (:path a)))))
        (let [object (edn/read-file (:path a))]
          (spit (:path a) (pr-str (assoc-in object [:payload :result :probabilities] [0.0 1.0])))
          (is (thrown? clojure.lang.ExceptionInfo (bundle/read! (:path a)))))
        (is (not-any? #(.endsWith (.getName %) ".tmp") (file-seq dir)))))))
