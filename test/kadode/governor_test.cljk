(ns kadode.governor-test
  "Proves kadode's G1/G7 boundary by making it REFUSE — for the reason it names.

  The discipline here is the one this workspace keeps re-learning: a negative test that only
  asserts \"it was rejected\" counts a rejection that happened for some OTHER reason as a
  success. So every case below starts from a record the gate ADMITS, mutates exactly ONE field,
  and pins the resulting reason literal. If a mutation ever starts failing earlier for an
  unrelated reason, the pinned literal changes and this suite goes red — which is the point.

  Three floors guard against the suite quietly measuring nothing:
    · the lexicons must actually load (otherwise every case refuses :record/unknown-type and
      the suite would look like it was discriminating when it was only failing to read files);
    · the valid baselines must be ADMITTED (a gate that refuses everything cannot pass);
    · every reason in `governor/refusal-reasons` must appear in the case table, so a branch
      added to the gate without a case here fails the suite.

  Coverage is derived from the table rather than from a mutable counter, so it does not depend
  on the order clojure.test happens to walk the vars in."
  (:require [clojure.test :refer [deftest is testing]]
            [kadode.governor :as gov]
            #?(:clj [clojure.java.io :as io])))

(def lexicons
  #?(:clj (gov/load-lexicons (io/file (System/getProperty "user.dir") "lex"))
     :cljs {}))

;; ── baselines: shapes the gate must ADMIT ──────────────────────────────────────

(def valid-relay
  {"$type" gov/relay-type
   "workerDid" "did:plc:qz3n7x2k4m8v6b1c9d0e5f7g"
   "scenario" "sc.taishoku-todoke"
   "documentCid" "bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku"
   "documentSha256" (str "0x" (apply str (repeat 64 "a")))
   "role" "messenger-使者"
   "negotiates" false
   "status" "drafted-unsent"
   "createdAt" "2026-08-31T00:00:00Z"
   "statutoryBasis" "民法627条1項（一方的解約・承諾不要）"
   "employerRef" "sha256:9f2b"})

(def valid-escalation
  {"$type" gov/escalation-type
   "scenario" "sc.mibarai-chingin"
   "relayed" false
   "escalateActor" ":labor-union"
   "escalateTo" "r.union"
   "reason" "この事案は交渉を要するため、kadode は使者として伝達できません (G1 / 弁護士法72条)。"
   "createdAt" "2026-08-31T00:00:00Z"})

;; ── the case table: one single-field mutation per declared refusal reason ──────

(def refusal-cases
  [{:reason :g1/negotiation-must-not-relay
    :why "the constitutional case — an otherwise-perfect relay for a matter needing negotiation"
    :record valid-relay
    :ctx {:needs-negotiation? true}}

   {:reason :g1/negotiates-must-be-false
    :why "lexicon: negotiates MUST be false; a true value is structurally invalid"
    :record (assoc valid-relay "negotiates" true)}

   {:reason :g1/escalation-must-not-claim-relay
    :why "lexicon: relayed MUST be false — kadode did NOT relay this matter"
    :record (assoc valid-escalation "relayed" true)}

   {:reason :g7/outward-send-needs-consent
    :why "\"sent\" IS in the status enum, so only G7 can catch a record minted already sent"
    :record (assoc valid-relay "status" "sent")}

   {:reason :record/unknown-type
    :why "a $type outside the two published lexicons"
    :record (assoc valid-relay "$type" "com.example.notKadode")}

   {:reason :record/missing-required-field
    :why "workerDid is in the lexicon's required list"
    :record (dissoc valid-relay "workerDid")}

   {:reason :record/field-not-in-enum
    :why "role's enum is [\"messenger-使者\"] — 代理人 is exactly what kadode must never be"
    :record (assoc valid-relay "role" "agent-代理人")}

   {:reason :record/field-not-in-enum
    :why "escalateActor's enum is the two actors who may lawfully negotiate"
    :record (assoc valid-escalation "escalateActor" ":kadode-messenger")}

   {:reason :record/field-length-out-of-bounds
    :why "documentSha256 is bounded 66/66 by the lexicon"
    :record (assoc valid-relay "documentSha256" "0xdeadbeef")}

   {:reason :record/document-sha256-malformed
    :why "right length (66), wrong alphabet — the prose says lowercase hex; the schema cannot"
    :record (assoc valid-relay "documentSha256" (str "0x" (apply str (repeat 64 "A"))))}])

;; ── floors ─────────────────────────────────────────────────────────────────────

(deftest lexicons-actually-loaded
  (is (= 2 (count lexicons))
      (str "expected both published lexicons, got " (pr-str (keys lexicons))))
  (is (some? (gov/record-schema (get lexicons gov/relay-type))))
  (is (some? (gov/record-schema (get lexicons gov/escalation-type)))))

(deftest admits-the-valid-baselines
  (is (:admitted? (gov/admit lexicons valid-relay))
      (str "valid relay was refused: " (pr-str (gov/admit lexicons valid-relay))))
  (is (:admitted? (gov/admit lexicons valid-escalation))
      (str "valid escalation was refused: " (pr-str (gov/admit lexicons valid-escalation))))
  (testing "a non-negotiating matter may be relayed — the G1 cross-check must not over-fire"
    (is (:admitted? (gov/admit lexicons valid-relay {:needs-negotiation? false}))))
  (testing "the escalation is the lawful answer for a negotiating matter"
    (is (:admitted? (gov/admit lexicons valid-escalation {:needs-negotiation? true})))))

(deftest every-declared-reason-has-a-case
  (let [covered (set (map :reason refusal-cases))
        missing (remove covered gov/refusal-reasons)]
    (is (empty? missing)
        (str "refusal reasons the gate declares but no case exercises: " (pr-str (vec missing)))))
  (is (>= (count refusal-cases) (count gov/refusal-reasons))
      "evidence floor: fewer cases than declared reasons"))

;; ── each refusal, for its own reason ───────────────────────────────────────────

(deftest refuses-each-case-for-its-named-reason
  (doseq [{:keys [reason record ctx why]} refusal-cases]
    (testing why
      (let [v (gov/admit lexicons record (or ctx {}))]
        (is (false? (:admitted? v)) (str "expected a refusal for " reason))
        (is (= reason (:reason v))
            (str "expected refusal " reason " but got " (:reason v)
                 " — a case that fails for the wrong reason proves nothing"))))))

(deftest admit-bang-is-fail-closed
  (testing "throws, naming the reason in ex-data"
    (let [e (try (gov/admit! lexicons (assoc valid-relay "negotiates" true))
                 nil
                 (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) ex ex))]
      (is (some? e) "admit! must throw on a refused record")
      (is (= :g1/negotiates-must-be-false (:reason (ex-data e))))))
  (testing "returns the record itself when admitted"
    (is (= valid-relay (gov/admit! lexicons valid-relay)))))
