(ns hazel.report-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.xml :as xml]
            [hazel.io.report :as report]))

(defn- path-elements [svg]
  (let [tree (xml/parse (java.io.ByteArrayInputStream. (.getBytes svg java.nio.charset.StandardCharsets/UTF_8)))]
    (filter #(and (map? %) (= :path (:tag %))) (tree-seq map? :content tree))))

(deftest svg-axes-have-no-filled-interior-regression
  ;; An open SVG path still gets a filled closing segment unless fill is disabled.
  ;; The original plot rendered a black triangle across its data area.
  (doseq [svg [(report/bar-chart "Marginal" [[0 0.2] [1 0.8]])
               (report/line-chart "Trajectory" [{:name "mean" :points [[0 0] [1 1]]}])]
          path (path-elements svg)]
    (is (= "none" (get-in path [:attrs :fill])))))
