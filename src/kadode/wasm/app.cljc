(ns kadode.wasm.app
  "kadode 門出 — kotoba pywasm component entry (ADR-2606112238). 1:1 Clojure port of
  `wasm/app.py`.

  Implements the five `etzhayyim:kadode/kadode-actor` world exports (wit/world.wit): analyze /
  datoms / coverage / generate / relay. The bounded PUBLIC labour-law seed is loaded read-only;
  G1 (使者-not-agent) + no-server-key hold (the `relay` export builds an UNSENT record and
  refuses negotiation-needing scenarios).

  Reuses the actor's real method siblings (analyze / datom-emit / coverage-report / generate).
  `analyze` + `relay` return JSON strings (ensure_ascii=False parity — UTF-8 kept literal);
  `datoms` + `coverage` + `generate` return the methods' own EDN/markdown/document text.
  Self-contained tiny JSON reader + encoder (no cheshire/data.json)."
  (:require [clojure.string :as str]
            [kadode.methods.analyze :as analyze]
            [kadode.methods.datom-emit :as datom-emit]
            [kadode.methods.coverage-report :as coverage]
            [kadode.methods.generate :as generate]
            #?(:clj [clojure.java.io :as io])))

;; ── tiny JSON encoder (ensure_ascii=False parity: non-ASCII kept literal) ───────
(defn- json-escape ^String [^String s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(declare ->json)

(defn- json-entries [pairs]
  (str "{" (str/join "," (map (fn [[k v]] (str "\"" (json-escape (str k)) "\":" (->json v))) pairs)) "}"))

(defn ->json
  "Encode Clojure data → a JSON string. Maps keep their seq/iteration order (use array-map to
  control key order); doubles that are integral print without a decimal point."
  [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (str "\"" (json-escape v) "\"")
    (integer? v) (str v)
    (double? v) (if (and (== v (Math/rint v)) (< (Math/abs (double v)) 1e15))
                  (str (long v))
                  (str v))
    (number? v) (str v)
    (map? v) (json-entries (seq v))
    (sequential? v) (str "[" (str/join "," (map ->json v)) "]")
    :else (str "\"" (json-escape (str v)) "\"")))

;; ── tiny JSON reader (for the `fields` argument of generate/relay) ─────────────
(declare json-value)

(defn- skip-ws [^String s i]
  (loop [i i]
    (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
      (recur (inc i)) i)))

(defn- json-string [^String s i]
  (loop [i (inc i), sb (StringBuilder.)]
    (let [c (nth s i)]
      (cond
        (= c \") [(.toString sb) (inc i)]
        (= c \\)
        (let [e (nth s (inc i))]
          (case e
            \" (do (.append sb \") (recur (+ i 2) sb))
            \\ (do (.append sb \\) (recur (+ i 2) sb))
            \/ (do (.append sb \/) (recur (+ i 2) sb))
            \b (do (.append sb \backspace) (recur (+ i 2) sb))
            \f (do (.append sb \formfeed) (recur (+ i 2) sb))
            \n (do (.append sb \newline) (recur (+ i 2) sb))
            \r (do (.append sb \return) (recur (+ i 2) sb))
            \t (do (.append sb \tab) (recur (+ i 2) sb))
            \u (let [cp (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)]
                 (.append sb (char cp)) (recur (+ i 6) sb))
            (do (.append sb e) (recur (+ i 2) sb))))
        :else (do (.append sb c) (recur (inc i) sb))))))

(defn- json-number [^String s i]
  (let [end (loop [j i]
              (if (and (< j (count s))
                       (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                (recur (inc j)) j))
        tok (subs s i end)]
    [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))

(defn- json-array [^String s i]
  (loop [i (skip-ws s (inc i)), out []]
    (if (= (nth s i) \])
      [out (inc i)]
      (let [[v i] (json-value s i)
            i (skip-ws s i)]
        (if (= (nth s i) \,)
          (recur (skip-ws s (inc i)) (conj out v))
          [(conj out v) (inc i)])))))

(defn- json-object [^String s i]
  (loop [i (skip-ws s (inc i)), out {}]
    (if (= (nth s i) \})
      [out (inc i)]
      (let [[k i] (json-string s i)
            i (skip-ws s i)
            [v i] (json-value s (skip-ws s (inc i)))
            out (assoc out k v)
            i (skip-ws s i)]
        (if (= (nth s i) \,)
          (recur (skip-ws s (inc i)) out)
          [out (inc i)])))))

(defn- json-value [^String s i]
  (let [i (skip-ws s i), c (nth s i)]
    (cond
      (= c \{) (json-object s i)
      (= c \[) (json-array s i)
      (= c \") (json-string s i)
      (= c \t) [true (+ i 4)]
      (= c \f) [false (+ i 5)]
      (= c \n) [nil (+ i 4)]
      :else (json-number s i))))

(defn parse-json
  "Parse the first JSON value in text → Clojure data (maps string-keyed)."
  [text]
  (first (json-value text 0)))

;; ── seed loading (host edge) ──────────────────────────────────────────────────
;; *file* is only bound during namespace load (it derefs to nil at call time), so capture the
;; actor dir at load and read the on-disk seed lazily from it (dev/host mode; no embedded module
;; in the .cljc port).
#?(:clj (def ^:private actor-dir (io/file ".")))

#?(:clj
   (defn- seed-text []
     (slurp (io/file actor-dir "data" "seed-resignation-graph.kotoba.edn"))))

#?(:clj
   (defn- load* []
     (datom-emit/load-graph* (analyze/read-edn (seed-text)))))

(defn analyze
  "JSON export: routes (route/actor/needs_negotiation/can_negotiate per scenario) +
  ground_support + risk_coverage. Mirrors app.analyze."
  []
  (let [{:keys [nodes edges]} (load*)
        res (analyze/analyze nodes edges)
        routes (reduce (fn [m [sid r]]
                         (assoc m sid (array-map
                                       "route" (get r "route")
                                       "actor" (get r "route_actor")
                                       "needs_negotiation" (get r "needs_negotiation")
                                       "can_negotiate" (get r "can_negotiate"))))
                       (array-map)
                       (get res "routes"))]
    (->json (array-map
             "routes" routes
             "ground_support" (get res "ground_support")
             "risk_coverage" (get res "risk_coverage")))))

(defn datoms
  "EDN export: the kotoba Datom log at tx. Mirrors app.datoms."
  ([] (datoms 1))
  ([tx]
   (let [{:keys [nodes edges]} (load*)]
     (datom-emit/emit nodes edges (analyze/analyze nodes edges) (long tx)))))

(defn coverage
  "Markdown export: the labour-exit coverage report. Mirrors app.coverage."
  []
  (let [{:keys [nodes edges]} (load*)]
    (coverage/report nodes edges)))

(defn generate
  "Document export: render the worker's OWN resignation document. Mirrors app.generate.
  `fields-json` is a JSON object string (or empty)."
  [kind fields-json]
  (generate/render kind (if (str/blank? (str fields-json)) {} (parse-json fields-json))))

(defn relay
  "JSON export: build the 使者 relay (or escalation) record. Mirrors app.relay."
  [scenario-id fields-json worker-did employer-ref]
  (let [{:keys [nodes edges]} (load*)
        fields (if (str/blank? (str fields-json)) {} (parse-json fields-json))
        doc (generate/render "taishoku-todoke" fields)]
    (->json (generate/build-relay scenario-id doc worker-did employer-ref nodes edges))))
