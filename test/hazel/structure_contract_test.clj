(ns hazel.structure-contract-test
  "Repository structure and publication contracts complement behavioral tests."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hazel.math.graph :as graph]))

(defn- production-namespaces []
  (let [root (.toPath (.getCanonicalFile (io/file "src")))]
    (mapv
     (fn [file]
       (with-open [reader (java.io.PushbackReader. (io/reader file))]
         (let [form (binding [*read-eval* false] (read reader))
               path (str/replace (str (.relativize root (.toPath (.getCanonicalFile file)))) "\\" "/")
               expected (-> path (str/replace #"\.clj$" "") (str/replace "/" ".") (str/replace "_" "-") symbol)
               requires (mapcat rest (filter #(and (seq? %) (= :require (first %))) (drop 2 form)))
               dependencies (set (map #(if (vector? %) (first %) %) requires))]
           {:path (str file) :namespace (second form) :expected expected :dependencies dependencies})))
     (sort-by str (filter #(and (.isFile %) (.endsWith (.getName %) ".clj")) (file-seq (io/file "src")))))))

(deftest namespace-paths-and-dependency-graph
  (let [entries (production-namespaces) names (set (map :namespace entries))
        graph (into {} (map #(vector (:namespace %) (set (filter (fn [n] (str/starts-with? (str n) "hazel.")) (:dependencies %)))) entries))]
    (is (= (count entries) (count names)))
    (doseq [entry entries]
      (is (= (:expected entry) (:namespace entry)) (:path entry))
      (require (:namespace entry))
      (doseq [dependency (get graph (:namespace entry))]
        (is (names dependency) (str "Missing internal dependency " dependency))))
    (is (every? #(= 1 (count %)) (graph/strongly-connected graph)))))

(deftest documentation-links-and-prose
  (let [files (concat [(io/file "README.md") (io/file "THIRD_PARTY.md")]
                      (filter #(and (.isFile %) (.endsWith (.getName %) ".md")) (file-seq (io/file "docs"))))]
    (doseq [file files :let [text (slurp file)]]
      (testing (str file)
        (is (not (str/includes? text (str (char 0x2014)))) "Disallowed prose punctuation")
        (doseq [[_ raw] (re-seq #"\]\(([^)]+)\)" text)
                :let [target (first (str/split raw #"#"))]
                :when (and (seq target) (not (re-find #"^[A-Za-z]+:" target)))]
          (is (.exists (io/file (.getParentFile file) target)) (str "Missing link target " target)))))))
