(ns kadode.methods.test-cid
  "kadode 門出 — CIDv1 content-address tests (ADR-2606112238).

  There is no test_cid.py in the source (cid.py is exercised transitively by test_generate.py's
  documentCid assertion). This suite pins the ported `cid.cljc` byte-for-byte against vectors
  computed by `python3 methods/cid.py` / hashlib — CIDv1 raw (0x55) / sha2-256 / multibase
  base32-lower 'b', and the 0x-prefixed sha256 hex — so any drift from python3 fails here."
  (:require [clojure.test :refer [deftest is run-tests]]
            [kadode.methods.cid :as cid]))

;; vectors verified byte-identical against python3 hashlib + cid.py (empty / ascii / 日本語 / ㊞).
(def vectors
  [["" "bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku"
       "0xe3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"]
   ["hello" "bafkreibm6jg3ux5qumhcn2b3flc3tyu6dmlb4xa7u5bf44yegnrjhc4yeq"
            "0x2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"]
   ["退職届\n一身上の都合により" "bafkreifr5qyf3wfkj5zz35hm2ugwkdhqtsc4qfuyywxhqiqds52hnh2kma"
                          "0xb1ec305dd8aa4f739df4ecd50d650cf09c85c81698c5ae7822039774769f4a60"]
   ["山田太郎㊞" "bafkreif3djyvorxhjwolxylzrqj7sqryu5qersd5zh22j7j24vkso2m6mq"
              "0xbb1a715746e74d9cbbe1798c13f94238a76048c87dc9f5a4fd3ae55527699e64"]])

(deftest test-cidv1-raw-byte-identical-to-python3
  (doseq [[s want-cid _] vectors]
    (is (= want-cid (cid/cidv1-raw s)) (str "CID mismatch for " (pr-str s)))))

(deftest test-sha256-hex-byte-identical-to-python3
  (doseq [[s _ want-hex] vectors]
    (is (= want-hex (cid/sha256-hex s)) (str "sha256-hex mismatch for " (pr-str s)))))

(deftest test-cid-deterministic
  (doseq [[s _ _] vectors]
    (is (= (cid/cidv1-raw s) (cid/cidv1-raw s)) "CID must be deterministic")))

(deftest test-cidv1-raw-shape
  ;; CIDv1 raw sha2-256 over a single block always renders as a 'bafkrei…' base32 string.
  (is (.startsWith ^String (cid/cidv1-raw "anything") "bafkrei")))
