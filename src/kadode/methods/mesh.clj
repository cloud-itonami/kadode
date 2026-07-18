;; mesh.clj — kadode 門出 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kadode (labour-resignation concierge + 使者).
;; Service mesh pattern (SERVICE-MESH-PATTERN.md): request-driven, :on-http trigger.
;; The on-http handler records the resignation scenario and returns coded steps +
;; escalation routes via Datalog. The full coded labour-law registry stays in methods.
;;
;; Posture: 使者 NOT 代理人 (弁護士法72) — relays the member's own resignation, NEVER
;; negotiates terms/severance (those route to a union/lawyer); default self-submit.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kadode)

(defn handle []
  ;; record the resignation scenario's coded steps; return as 使者/self-submit guidance.
  (kqe-assert! "kadode" "standard-resignation" "step" "notice-627")
  (kqe-assert! "kadode" "negotiation-needed" "step" "route-to-union")
  (kqe-query "guidance(?s) :- step(?s)."))

(defn run [ctx] (handle))
(defn on-http [req] (handle))
