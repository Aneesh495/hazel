(ns hazel.io.report
  "Self-contained, script-free HTML/SVG reports generated entirely in Clojure."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [hazel.io.bundle :as bundle]
            [hazel.math.rational :as rational]
            [hazel.math.numeric :as n]))

(defn escape [text]
  (str/escape (str text) {\& "&amp;" \< "&lt;" \> "&gt;" \" "&quot;" \' "&#39;"}))

(defn format-number [x]
  (if (number? x)
    (cond (not (n/finite? x)) (str x)
          (and (>= (abs x) 0.001) (< (abs x) 10000)) (format "%.4f" (double x))
          (zero? x) "0"
          :else (format "%.3e" (double x)))
    (escape x)))

(defn format-rational-bound
  "Decimal presentation must enclose the exact ratio, including rounding."
  [value side]
  (rational/exact! :display-bound value)
  (let [numerator (if (ratio? value) (numerator value) value)
        denominator (if (ratio? value) (denominator value) 1)
        mode (case side :lower java.math.RoundingMode/FLOOR :upper java.math.RoundingMode/CEILING
                   (n/fail "Unknown bound side" {:side side}))]
    (.toPlainString (.divide (java.math.BigDecimal. (str numerator))
                            (java.math.BigDecimal. (str denominator)) 16 mode))))

(defn- svg [title content]
  (str "<svg viewBox=\"0 0 520 260\" role=\"img\" aria-label=\"" (escape title) "\">"
       "<title>" (escape title) "</title>" content "</svg>"))

(defn- label [x y text]
  (str "<text x=\"" x "\" y=\"" y "\">" (escape text) "</text>"))

(defn bar-chart [title entries]
  (let [entries (vec entries) maximum (reduce max 0.001 (map second entries))
        count (max 1 (count entries)) width (/ 450.0 count)
        stride (max 1 (long (Math/ceil (/ count 12.0))))]
    (svg title
         (str "<path class=\"axis\" fill=\"none\" d=\"M45 20V220H505\"/>"
              (label 6 25 (format-number maximum)) (label 25 222 "0")
              (apply str (map-indexed
                          (fn [i [x probability]]
                            (let [height (* 185 (/ probability maximum)) left (+ 49 (* i width))]
                              (str "<rect class=\"bar\" x=\"" left "\" y=\"" (- 219 height)
                                   "\" width=\"" (max 0.2 (- width 1.4)) "\" height=\"" height "\">"
                                   "<title>" (escape x) ": " (format-number probability) "</title></rect>"
                                   (when (zero? (mod i stride)) (label left 242 x))))) entries))))))

(defn line-chart [title series]
  (let [points (mapcat :points series)
        xmax (reduce max 1e-9 (map first points)) ymax (reduce max 1e-9 (map second points))
        x #(+ 45 (* 450 (/ % xmax))) y #(- 220 (* 190 (/ % ymax)))]
    (svg title
         (str "<path class=\"axis\" fill=\"none\" d=\"M45 20V220H505\"/>"
              (label 6 25 (format-number ymax)) (label 25 222 "0")
              (label 45 244 "0") (label 455 244 (format-number xmax))
              (apply str (map-indexed
                          (fn [i {:keys [name points]}]
                            (str "<polyline class=\"curve " (when (odd? i) "secondary") "\" points=\""
                                 (str/join " " (map #(str (x (first %)) "," (y (second %))) points)) "\">"
                                 "<title>" (escape name) "</title></polyline>"
                                 (label (+ 65 (* i 160)) 18 name))) series))))))

(defn- marginal [states probabilities index]
  (reduce (fn [result [state p]] (update result (state index) (fnil + 0.0) p))
          (sorted-map) (map vector states probabilities)))

(defn- population-mean [states probabilities index]
  (n/sum (map #(* (nth %1 index) %2) states probabilities)))

(defn- reaction-side [side]
  (if (empty? side) "∅"
      (str/join " + " (for [[species order] (sort-by (comp str key) side) :when (pos? order)]
                          (str (when (> order 1) order) (name species))))))

(defn- reaction-table [model]
  (apply str (for [reaction (:reactions model)]
               (str "<div class=\"reaction\"><span class=\"mono\">"
                    (escape (reaction-side (:reactants reaction))) " → "
                    (escape (reaction-side (:products reaction)))
                    "</span><small>" (escape (:id reaction)) " · k = "
                    (escape (:constant reaction)) "</small></div>"))))

(defn- table [headers rows]
  (str "<div class=\"scroll\"><table><thead><tr>"
       (apply str (map #(str "<th>" (escape %) "</th>") headers)) "</tr></thead><tbody>"
       (apply str (map (fn [row] (str "<tr>" (apply str (map #(str "<td>" (escape %) "</td>") row)) "</tr>")) rows))
       "</tbody></table></div>"))

(defn- card [title content]
  (str "<section class=\"card\"><h2>" (escape title) "</h2>" content "</section>"))

(defn render [model request result digest]
  (let [states (:states result) probabilities (:probabilities result)
        history (:history result) species (:species model)
        trajectory (:trajectory result)
        steps (when (vector? (:steps result)) (:steps result))
        certificate (:certificate result)
        final-probabilities (or probabilities (:lower-probabilities certificate) (:probabilities (peek trajectory)))
        charts (when (and (seq states) final-probabilities)
                 (apply str (for [[i name] (take 6 (map-indexed vector species))]
                              (card (str (clojure.core/name name) " population")
                                    (str "<h3>" (if certificate "Certified lower probability" "Retained probability") " by copy number</h3>"
                                         (bar-chart (str name " marginal") (marginal states final-probabilities i)))))))
        timeline (when (and (seq states) (or trajectory (some :filtered steps)))
                   (let [series (or trajectory steps)]
                     (card "Population over time"
                           (line-chart "Expected captured population"
                                       (mapv (fn [[i name]]
                                               {:name (clojure.core/name name)
                                                :points (mapv #(vector (:time %)
                                                                       (population-mean states (or (:probabilities %) (:filtered %)) i)) series)})
                                             (take 2 (map-indexed vector species)))))))
        raw (with-out-str (pprint/pprint (bundle/portable (dissoc result :states :events :trajectory :steps))))
        css (slurp (io/resource "hazel/report.css"))]
    (str "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
         "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
         "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">"
         "<title>Hazel · " (escape (:title result (:name model))) "</title><style>" css "</style></head><body><main>"
         "<header><div class=\"eyebrow\">Hazel / stochastic reaction laboratory</div><h1>"
         (escape (:title result (:name model))) "</h1><p class=\"subtitle\">Discrete populations. Continuous time. "
         "An explicit account of the probability retained by the calculation.</p><span class=\"pill\">"
         (escape (name (:kind request))) " · " (escape (:status result)) "</span>"
         "<div class=\"metrics\">"
         (apply str (for [[label value] [["Species" (count species)] ["Reactions" (count (:reactions model))]
                                        ["Projected states" (if states (count states) "sample paths")]
                                        ["Escaped probability" (if (contains? result :escape-probability)
                                                                  (format-number (:escape-probability result)) "see diagnostics")]]]
                      (str "<div class=\"metric\"><strong>" (escape value) "</strong><span>" label "</span></div>")))
         "</div></header><div class=\"grid\">"
         (when-let [interval (:event-interval result)]
           (card "Certified event enclosure"
                 (str "<p class=\"muted\">Exact rational bounds, independently checked from source reactions.</p>"
                      (table ["Lower bound, rounded down" "Upper bound, rounded up" "Exact width, decimal display"]
                             [[(format-rational-bound (:lower interval) :lower) (format-rational-bound (:upper interval) :upper)
                               (format-number (- (:upper interval) (:lower interval)))]]))))
         charts timeline
         (when (seq (:first-passage result))
           (card "First-passage probability"
                 (line-chart "First-passage interval"
                             [{:name "lower" :points (mapv #(vector (:time %) (:lower %)) (:first-passage result))}
                              {:name "upper" :points (mapv #(vector (:time %) (:upper %)) (:first-passage result))}])))
         (when (and (seq history) (:escape (first history)))
           (card "Projection refinement"
                 (table ["Attempt" "States" "Escape" "Series tail"]
                        (map #(vector (:attempt %) (:states %) (format-number (:escape %)) (format-number (:series-tail %))) history))))
         (card "Reaction network" (reaction-table model))
         (card "Reading this result"
               (str (if certificate
                      "<p class=\"muted\">This result uses exact integer and ratio arithmetic. Its mass deficit accounts for omitted positive series terms and the rational exponential enclosure. The independent checker reconstructs every transition from source reactions.</p>"
                      "<p class=\"muted\">Escape is probability that has left the finite projection. The Poisson tail is a separate series approximation. Neither includes a formal bound on binary64 roundoff.</p>")
                    (when (:projection-conditioned? result)
                      "<p class=\"warning\">Posterior calculations condition on paths retained by the projection. Compare larger projections before interpreting them.</p>")
                    "<p class=\"muted\">Sample-path uncertainty is statistical. A completed numerical run does not imply a scientifically adequate model.</p>"))
         "</div>" (card "Numerical record"
                              (str "<p class=\"mono muted\">Model SHA-256: " (escape (:hash model)) "</p>"
                                   "<details><summary>Inspect structured results</summary><pre>"
                                   (escape (subs raw 0 (min 100000 (count raw)))) "</pre></details>"))
         "<footer>Hazel 0.1.0 · " (escape digest) "<br>Self-contained HTML and SVG. No scripts, remote assets, or telemetry.</footer>"
         "</main></body></html>")))

(defn write! [path model request result digest]
  (bundle/atomic-write! path (render model request result digest)))
