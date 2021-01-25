(ns hazel.io.edn
  "Inert, single-value EDN and stable hashes. Never uses the Clojure code reader."
  (:refer-clojure :exclude [read-string])
  (:require [clojure.edn :as edn]
            [clojure.string]
            [clojure.java.io :as io]
            [hazel.math.numeric :as n])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(def default-byte-limit (* 4 1024 1024))

(defn read-string
  ([text] (read-string text default-byte-limit))
  ([text limit]
   (when-not (string? text) (n/fail "EDN input must be text" {}))
   (n/natural! :byte-limit limit)
   (when (> (alength (.getBytes ^String text StandardCharsets/UTF_8)) limit)
     (n/fail "EDN exceeds byte limit" {:limit limit}))
   (try
     (let [eof (Object.)
           reject (fn [value] (n/fail "Tagged EDN literals are not supported" {:value (str value)}))
           options {:eof eof :readers {'inst reject 'uuid reject}
                    :default (fn [tag _] (n/fail "Unknown EDN tag" {:tag tag}))}]
       (with-open [reader (java.io.PushbackReader. (java.io.StringReader. text))]
         (let [value (edn/read options reader)]
           (when (identical? eof value) (n/fail "EDN input is empty" {}))
           (when-not (identical? eof (edn/read options reader))
             (n/fail "Expected exactly one EDN value" {}))
           value)))
     (catch StackOverflowError _ (n/fail "EDN nesting is too deep" {}))
     (catch RuntimeException e
       (if (:hazel/error (ex-data e)) (throw e)
           (n/fail "Malformed EDN" {:reason (.getMessage e)}))))))

(defn read-file
  ([path] (read-file path default-byte-limit))
  ([path limit]
   ;; Stream at most limit+1 bytes. A metadata size check alone has a TOCTOU race.
   (with-open [stream (io/input-stream path)]
     (let [bytes (.readNBytes stream (int (inc limit)))]
       (when (> (alength bytes) limit) (n/fail "EDN exceeds byte limit" {:limit limit}))
       (read-string (String. bytes StandardCharsets/UTF_8) limit)))))

(defn canonical
  "Deterministic textual data representation. Order-independent maps and sets."
  [value]
  (letfn [(emit [x depth]
            (when (> depth 256) (n/fail "Canonical data nesting is too deep" {}))
            (let [child #(emit % (inc depth))]
              (cond
                (map? x) (str "{" (clojure.string/join " "
                                      (map (fn [[k v]] (str (child k) " " (child v)))
                                           (sort-by (comp child key) x))) "}")
                (set? x) (str "#{" (clojure.string/join " " (sort (map child x))) "}")
                (vector? x) (str "[" (clojure.string/join " " (map child x)) "]")
                (or (nil? x) (boolean? x) (string? x) (keyword? x)
                    (and (number? x) (n/finite? x))) (pr-str x)
                :else (n/fail "Unsupported canonical data type" {:class (str (type x))}))))]
    (emit value 0)))

(defn sha256-bytes [bytes]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") ^bytes bytes)]
    (apply str (map #(format "%02x" (bit-and 0xff %)) digest))))

(defn sha256 [value]
  (sha256-bytes (.getBytes ^String (canonical value) StandardCharsets/UTF_8)))
