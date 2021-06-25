(ns hazel.report-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.xml :as xml]
            [hazel.fixtures :as fixtures]
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

(deftest displayed-rational-enclosure-rounds-outward-regression
  (let [html (report/render fixtures/birth {:kind :certify}
                             {:status :complete :certificate {}
                              :event-interval {:lower 1/3 :upper 1/3}} "test")
        displayed (mapv (comp rationalize bigdec second) (re-seq #"<td>([0-9.]+)</td>" html))]
    (is (<= (first displayed) 1/3 (second displayed)))))
