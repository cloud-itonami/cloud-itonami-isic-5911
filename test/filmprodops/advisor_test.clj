(ns filmprodops.advisor-test
  "Unit tests of `filmprodops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [filmprodops.advisor :as adv]
            [filmprodops.store :as store]))

(def db (store/seed-db))

(deftest propose-production-record-shape
  (testing "production-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-production-record
                           :production-id "production-1"
                           :patch {:scene "12A" :take 3}})]
      (is (= :log-production-record (:op p)))
      (is (= "production-1" (:production-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :production-id)))))

(deftest propose-production-schedule-shape
  (testing "production-schedule proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-production-operation
                           :production-id "production-2"
                           :patch {:location "soundstage 2" :date "2026-07-20"}})]
      (is (= :schedule-production-operation (:op p)))
      (is (= "production-2" (:production-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-onset-safety-concern-shape
  (testing "on-set safety-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-onset-safety-concern
                           :production-id "production-1"
                           :patch {:concern "elevated risk on rooftop chase sequence"}})]
      (is (= :flag-onset-safety-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-post-production-handoff-shape
  (testing "post-production handoff proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-post-production-handoff
                           :production-id "production-1"
                           :patch {:dailies "day-14" :destination "edit-bay-2"}})]
      (is (= :coordinate-post-production-handoff (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-social-distribution-handoff-shape
  (testing "social/platform distribution handoff proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-social-distribution-handoff
                           :production-id "production-1"
                           :patch {:platform "shorts" :destination "channel-a"}})]
      (is (= :coordinate-social-distribution-handoff (:op p)))
      (is (= "production-1" (:production-id p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-platform-content-policy-concern-shape
  (testing "platform content-policy-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-platform-content-policy-concern
                           :production-id "production-1"
                           :patch {:concern "possible community-guideline risk in the trailer cut"}})]
      (is (= :flag-platform-content-policy-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-production-record :schedule-production-operation
                :flag-onset-safety-concern :coordinate-post-production-handoff
                :coordinate-social-distribution-handoff :flag-platform-content-policy-concern]]
      (let [p (adv/infer db {:op op :production-id "production-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-production-record :schedule-production-operation
                :flag-onset-safety-concern :coordinate-post-production-handoff
                :coordinate-social-distribution-handoff :flag-platform-content-policy-concern]]
      (let [p (adv/infer db {:op op :production-id "production-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
