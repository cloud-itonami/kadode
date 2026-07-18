(ns kadode.methods.coverage-report
  "kadode 門出 — labour-exit COVERAGE report (ADR-2606112238). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage of the resignation graph: scenario / ground / route / risk spread, plus two
  integrity checks — (1) every scenario reaches a recommended lawful route, and (2) the UPL
  invariant holds (no negotiation-needing scenario resolves to a non-negotiating 使者/self
  route). Coverage of all employment situations is bounded by design (G5).

  Pure fns; reuses kadode.methods.analyze for the loader + route logic. Portable .cljc."
  (:require [clojure.string :as str]
            [clojure.set]
            [kadode.methods.analyze :as analyze]))

(def employment [":no-fixed-term" ":fixed-term" ":fixed-term-1yr+" ":probation" ":dispatch"])
(def route-actors [":worker-self" ":kadode-messenger" ":labor-union" ":lawyer"])
(def THIN 2)

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn report
  "Render the labour-exit coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (vals nodes)
        scen (filter #(= ":scenario" (get % ":lx/kind")) vals*)
        grounds (filter #(= ":ground" (get % ":lx/kind")) vals*)
        routes (filter #(= ":route" (get % ":lx/kind")) vals*)
        docs (filter #(= ":document" (get % ":lx/kind")) vals*)
        risks (filter #(= ":risk" (get % ":lx/kind")) vals*)
        emp-c (counter (map #(get % ":scenario/employment") scen))
        route-c (counter (map #(get % ":route/actor") routes))
        ;; integrity: every scenario routes, and UPL invariant holds (edge order = nodes order)
        [unrouted upl-violations]
        (reduce
         (fn [[un uv] s]
           (let [sid (get s ":lx/id")
                 rec (analyze/recommend-route sid nodes edges)
                 un (if (get rec "route") un (conj un sid))
                 uv (if (and (get rec "needs_negotiation")
                             (not (contains? analyze/negotiating-actors (get rec "route_actor"))))
                      (conj uv sid) uv)]
             [un uv]))
         [[] []]
         scen)
        countered (set (for [e edges :when (= ":counters" (get e ":en/kind"))] (get e ":en/to")))
        risk-ids (set (map #(get % ":lx/id") risks))
        uncountered (sort (clojure.set/difference risk-ids countered))
        L (transient [])]
    (conj! L "# kadode 門出 — labour-exit coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all employment situations is bounded by design "
                  "(G5). PUBLIC Japanese labour law only; kadode is a 使者 + concierge, never the "
                  "practice of law (G1).\n"))
    (conj! L (str "**Seed**: " (count scen) " scenarios · " (count grounds) " grounds · "
                  (count routes) " routes · " (count docs) " documents · " (count risks)
                  " employer-risk patterns · " (count edges) " 縁\n"))

    (letfn [(bucket [title ks cnt]
              (conj! L (str "\n## " title "\n"))
              (conj! L "| bucket | count | status |")
              (conj! L "|---|---:|:--|")
              (doseq [k ks]
                (let [c (get cnt k 0)
                      status (cond (= c 0) "— **MISSING**"
                                   (< c THIN) "⚠ thin"
                                   :else "ok")]
                  (conj! L (str "| " (lstrip-colon k) " | " c " | " status " |")))))]
      (bucket "Employment-type coverage" employment emp-c)
      (bucket "Route coverage (the escalation ladder)" route-actors route-c))

    (conj! L "\n## Integrity — routing completeness + the UPL invariant\n")
    (conj! L (str "- scenarios reaching a lawful route: **" (- (count scen) (count unrouted))
                  "/" (count scen) "**"
                  (if (empty? unrouted) "" (str " (unrouted: " (str/join ", " unrouted) ")"))))
    (conj! L (str "- UPL invariant (negotiation ⇒ union/lawyer, never 使者/self): "
                  (if (empty? upl-violations)
                    "**holds for all scenarios** ✓"
                    (str "**VIOLATED** by " (str/join ", " upl-violations) " ✗"))))
    (conj! L (str "- employer-risk patterns with a countering legal ground: "
                  "**" (- (count risk-ids) (count uncountered)) "/" (count risk-ids) "**"
                  (if (empty? uncountered) ""
                      (str " (uncountered: " (str/join ", " uncountered) ")"))))

    (let [miss-emp (for [e employment :when (= 0 (get emp-c e 0))] (lstrip-colon e))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (conj! L (if (seq miss-emp)
                 (str "Missing employment buckets: " (str/join ", " miss-emp) ".")
                 "No fully-missing employment buckets (thin buckets still listed above).")))
    (conj! L "\n---\n_kadode 門出 · ADR-2606112238 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-resignation-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "kadode coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
