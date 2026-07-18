(ns kadode.methods.analyze
  "kadode 門出 — edge-primary, UPL-bounded resignation-route analyzer over the labour-exit graph.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606112238).

  Reads a kotoba-EDN labour-exit graph (:lx/* nodes + :en/* 縁 over the labor-exit-ontology),
  and surfaces, per worker SCENARIO: the lawful escalation ROUTE (self → messenger → union →
  lawyer), the labour-law GROUNDS that support the exit, and how well each employer RISK pattern
  is answered by a legal ground — routed to a DIGNIFIED EXIT, never to a litigation promise.

  CONSTITUTIONAL (read before any change):
    G1 — 使者 not 代理人. kadode RELAYS a worker's already-formed unilateral resignation and
      DRAFTS their documents; it NEVER negotiates (弁護士法72条). The analyzer ENFORCES this: a
      scenario whose goal needs negotiation (:scenario/needs-negotiation true) is NEVER given a
      non-negotiating primary route (:worker-self / :kadode-messenger) — it escalates to
      :labor-union (団体交渉) or :lawyer. recommend-route raises (ex-info) if the graph violates
      this.
    N1 / G2 — edge-primary. route fit / ground support live ONLY on :en/weight edges, integrated
      on READ; no stored per-scenario score. The resignation is the WORKER'S own act.
    N3 / G3 — non-adjudicating. grounds/citations are DISCLOSED legal facts, never kadode
      verdicts; kadode never promises an outcome.

  House style: Python ':…' keyword strings stay strings (incl. all :lx/* / :en/* attrs);
  pure fns; file I/O only at edges via clojure.java.io. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, "string", num, bool, nil)
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse faithfully. Keywords are kept as
;; ":ns/name" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python port.

(def ^:private tok-re
  ;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (group 1 of each tok-re match that captured)."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t)
              (step)
              (cons t (step))))))))))

(defn atom-of
  "Port of _atom: \"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string;
  int → long; else float; else raw string."
  [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step
  "Consume one form from the token vector at index i. Returns [value next-i] or
  [end-marker next-i] when a closing ] or } is hit (matching _parse's _END sentinel)."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker)
            [out i]
            (recur i (conj out x)))))

      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── routes that may NEGOTIATE (the single load-bearing boundary; mirrors schema)
(def negotiating-actors #{":labor-union" ":lawyer"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Insertion order of nodes is preserved (ordered map) to match Python dict order."
  [forms]
  (reduce
   (fn [{:keys [nodes edges] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":lx/id") (assoc-in acc [:nodes (get f ":lx/id")] f)
       (and (contains? f ":en/from") (contains? f ":en/to"))
       (update acc :edges conj f)
       :else acc))
   {:nodes (array-map) :edges []}
   forms))

#?(:clj
   (defn load-file*
     "Read + parse a labour-exit EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->weight
  "float(e.get(':en/weight', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/weight")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn recommend-route
  "The lawful recommended route for a scenario (edge-primary, UPL-enforced).

  Picks the highest-weight :requires-route edge, BUT if the scenario needs negotiation the
  recommendation is constrained to a negotiating actor (union/lawyer) — kadode never relays a
  matter that requires negotiation (G1 / 弁護士法72条). Raises (ex-info) on a graph that
  violates this.

  Returns a string-keyed map mirroring the Python dict exactly."
  [scenario-id nodes edges]
  (let [sc (get nodes scenario-id {})
        needs-neg (boolean (get sc ":scenario/needs-negotiation"))
        ;; cands: vector of [weight to actor can-neg], in edge order (mirrors Python list append)
        cands (reduce
               (fn [acc e]
                 (if (and (= ":requires-route" (get e ":en/kind"))
                          (= scenario-id (get e ":en/from")))
                   (let [route (get nodes (get e ":en/to") {})
                         actor (get route ":route/actor")
                         can-neg (contains? negotiating-actors actor)]
                     (conj acc [(->weight e) (get e ":en/to") actor can-neg]))
                   acc))
               []
               edges)]
    (if (empty? cands)
      {"scenario" scenario-id "route" nil "needs_negotiation" needs-neg
       "candidates" []}
      (let [eligible (filterv (fn [c] (or (nth c 3) (not needs-neg))) cands)]
        (when (and needs-neg (not (some #(nth % 3) cands)))
          (throw (ex-info
                  (str "UPL violation in graph: scenario " scenario-id " needs negotiation but "
                       "has no union/lawyer route — a 使者/self route must never be the answer (G1)")
                  {:scenario scenario-id :needs-negotiation needs-neg})))
        ;; pick = max(eligible or cands, key=weight). Python max is stable → keeps the FIRST
        ;; maximal element. reduce with > (strict) preserves the first on ties.
        (let [pool (if (seq eligible) eligible cands)
              pick (reduce (fn [a c] (if (> (nth c 0) (nth a 0)) c a)) (first pool) (rest pool))
              ;; candidates sorted by -weight (stable; ties keep edge order, mirroring Python's
              ;; sorted(..., key=lambda x: -x[2]) on the (to, actor, weight) projection).
              cand-rows (->> cands
                             (map (fn [c] [(nth c 1) (nth c 2) (nth c 0)]))
                             (sort-by (fn [r] (- (nth r 2)))))]
          {"scenario" scenario-id
           "route" (nth pick 1)
           "route_actor" (nth pick 2)
           "needs_negotiation" needs-neg
           "can_negotiate" (nth pick 3)
           "candidates" (vec cand-rows)})))))

;; ── ordered accumulation (mirror a Python defaultdict's first-touch iteration order) ────
(defn- ordered-map [] ^{::order []} {})

(defn- omap-update
  "update an ordered-map: apply f to the value at k (default 0.0 via fnil), recording k's
  first-touch position in ::order metadata."
  [m k f]
  (let [had? (contains? m k)
        m' (update m k (fnil f 0.0))]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn analyze
  "Edge-primary integrals (computed on read; transient — N1/G2). Returns
   {\"ground_support\" {scenario v} \"risk_coverage\" {risk v} \"route_use\" {route v}
    \"routes\" {scenario rec}}.

   ground_support[sid]   = Σ :supported-by weight out of the scenario
   risk_coverage[rid]    = Σ inbound :counters weight
   route_use[route]      = Σ inbound :requires-route weight

   Accumulation maps carry ::order metadata = first-touch insertion order, so any stable
   sort ties exactly the Python defaultdict iteration order."
  [nodes edges]
  (let [{:keys [ground-support risk-coverage route-use]}
        (reduce
         (fn [acc e]
           (let [k (get e ":en/kind")
                 w (->weight e)]
             (cond
               (= k ":supported-by")
               (update acc :ground-support omap-update (get e ":en/from") #(+ % w))
               (= k ":counters")
               (update acc :risk-coverage omap-update (get e ":en/to") #(+ % w))
               (= k ":requires-route")
               (update acc :route-use omap-update (get e ":en/to") #(+ % w))
               :else acc)))
         {:ground-support (ordered-map) :risk-coverage (ordered-map) :route-use (ordered-map)}
         edges)
        scenarios (filterv #(= ":scenario" (get-in nodes [% ":lx/kind"])) (keys nodes))
        routes (reduce (fn [m s] (assoc m s (recommend-route s nodes edges)))
                       (array-map) scenarios)]
    {"ground_support" ground-support
     "risk_coverage" risk-coverage
     "route_use" route-use
     "routes" routes}))

(defn risk-counters
  "Per employer 引き止め RISK pattern, the legal GROUNDS that counter it — the worker's preparedness
  map: facing a tactic (退職拒否 / 損害賠償脅迫 / 有給拒否 …) they see the disclosed law that answers
  it (民法627 the unilateral right; 労基法16 no penalty-for-resigning; …). `analyze`'s risk_coverage
  gives each risk's total counter-WEIGHT; this names the SPECIFIC counter-grounds behind that number.
  Each ground is a DISCLOSED legal fact with a citation (G3/N3), never a kadode verdict — kadode
  INFORMS, it does not advise / represent / negotiate (G1 使者-not-代理人). A risk with no counter
  (a coverage gap) is included with an empty list. Returns [risk pattern label counter-grounds] (each
  ground = [id label]) by counter-count descending."
  [nodes edges]
  (let [counters (reduce (fn [m e]
                           (if (= ":counters" (get e ":en/kind"))
                             (update m (get e ":en/to") (fnil conj []) (get e ":en/from"))
                             m))
                         {} edges)]
    (->> nodes
         (filter (fn [[_ n]] (= ":risk" (get n ":lx/kind"))))
         (map (fn [[rid n]]
                [rid (get n ":risk/pattern" rid) (get n ":lx/label" "")
                 (mapv (fn [gid] [gid (get-in nodes [gid ":lx/label"] gid)]) (get counters rid []))]))
         (sort-by (fn [[rid _ _ grounds]] [(- (count grounds)) (str rid)]))
         vec)))

;; ── report rendering (matches report_md's f-strings) ────────────────────────

(defn- omap-items
  "Items of an ordered-map in first-touch order (falls back to seq order if no ::order)."
  [d]
  (let [order (::order (meta d))]
    (if order
      (map (fn [k] [k (get d k)]) order)
      (seq d))))

(defn- fmt2 [v] (format "%.2f" (double v)))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- count-kind [nodes k]
  (count (filter #(= k (get % ":lx/kind")) (vals nodes))))

(defn report-md
  "Render the labour-exit route-report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n (fn [k] (count-kind nodes k))
        L (transient [])]
    (conj! L "# kadode 門出 — labour-exit (退職代行) route report\n")
    (conj! L (str "> **G1 — kadode is a 使者 (messenger) + concierge, NEVER a 代理人 (agent) and "
                  "NEVER the practice of law.** It relays a worker's already-formed unilateral "
                  "resignation (民法627 = a unilateral right; the employer's consent is not required) "
                  "and drafts the worker's own documents. It does NOT negotiate (弁護士法72条) — any "
                  "matter needing negotiation is routed to a labour union (団体交渉) or a lawyer. "
                  "Statute citations are DISCLOSED facts (N3), never a kadode verdict or outcome "
                  "promise. Free, worker-authored, worker-submitted.\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" (n ":scenario") " scenarios · "
                  (n ":ground") " grounds · " (n ":document") " documents · " (n ":route")
                  " routes · " (n ":risk") " employer-risk patterns) · " (count edges) " 縁\n"))

    (conj! L "\n## Recommended lawful route per scenario (UPL-bounded)\n")
    (conj! L (str "_Negotiation-needing situations escalate to union/lawyer; kadode relays only "
                  "non-negotiating unilateral exits (G1)._\n"))
    (conj! L "| scenario | needs negotiation | route | actor | may negotiate? |")
    (conj! L "|---|:--:|---|---|:--:|")
    (doseq [sid (sort (keys (get res "routes")))]
      (let [r (get-in res ["routes" sid])
            label (get-in nodes [sid ":lx/label"] sid)
            route (get r "route")
            route-label (if route (get-in nodes [route ":lx/label"] route) "—")
            actor (get r "route_actor" "—")]
        (conj! L (str "| " label " | " (if (get r "needs_negotiation") "はい" "いいえ") " | "
                      route-label " | " (lstrip-colon (str actor)) " | "
                      (if (get r "can_negotiate") "○" "×") " |"))))

    (conj! L "\n## Ground support per scenario (how well-grounded the exit is in labour law)\n")
    (conj! L "| scenario | Σ ground support |")
    (conj! L "|---|---:|")
    (doseq [[sid v] (sort-by (fn [[_ v]] (- v)) (omap-items (get res "ground_support")))]
      (conj! L (str "| " (get-in nodes [sid ":lx/label"] sid) " | " (fmt2 v) " |")))

    (conj! L "\n## Employer-risk coverage (how strongly each 引き止め pattern is answered)\n")
    (conj! L "| employer risk | countering ground strength |")
    (conj! L "|---|---:|")
    (doseq [[rid v] (sort-by (fn [[_ v]] (- v)) (omap-items (get res "risk_coverage")))]
      (let [rn (get nodes rid {})]
        (conj! L (str "| " (get rn ":risk/pattern" rid) " — " (get rn ":lx/label" "") " | "
                      (fmt2 v) " |"))))

    (conj! L (str "\n---\n_kadode 門出 · ADR-2606112238 · 使者-not-agent · non-adjudicating · "
                  "edge-primary · dignified-exit-routed. Live relay/sending is G7-gated; worker "
                  "self-submits by default._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/route-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-resignation-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "route-report.md") (report-md nodes edges res))
       (println (str "kadode: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "route-report.md")))
       0)))
