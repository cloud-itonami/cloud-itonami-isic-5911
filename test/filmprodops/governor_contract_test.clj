(ns filmprodops.governor-contract-test
  "Integration tests: full OperationActor graph exercising the governor's
  hard checks, escalation logic, and audit trail."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [filmprodops.advisor :as advisor]
            [filmprodops.store :as store]
            [filmprodops.operation :as op]))

(defn exec-request [actor tid request ctx]
  (g/run* actor {:request request :context ctx} {:thread-id tid}))

(defn resume-approval [actor tid status]
  (g/run* actor {:approval {:status status :by "coordinator"}} {:thread-id tid :resume? true}))

(deftest production-record-logging-full-flow
  (testing "clean production-record proposal -> auto-commit at phase 3"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-1" :phase 3}
          result (exec-request actor "t1"
                               {:op :log-production-record :production-id "production-1" :patch {:scene "1A" :take 1}}
                               ctx)]
      (is (some? result))
      (is (> (count (store/ledger db)) 0)
          "commit must append audit facts to ledger")
      (is (> (count (store/coordination-log db)) 0)
          "commit must append record to coordination-log"))))

(deftest onset-safety-concern-always-escalates
  (testing ":flag-onset-safety-concern escalates for human approval, regardless of phase/confidence"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-2" :phase 3}
          result (exec-request actor "t2"
                               {:op :flag-onset-safety-concern :production-id "production-1"
                                :patch {:concern "stunt risk on rooftop sequence" :confidence 0.99}}
                               ctx)]
      (is (some? result))
      ;; At this point the actor is paused for approval, not yet committed
      (is (= 0 (count (store/coordination-log db)))
          "safety concern must not auto-commit, must wait for approval")
      ;; Now approve it
      (resume-approval actor "t2" :approved)
      (is (> (count (store/coordination-log db)) 0)
          "after approval, record must be committed"))))

(deftest unregistered-production-hard-hold
  (testing "unregistered production -> permanent HARD hold, never escalates"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-3" :phase 3}]
      (exec-request actor "t3"
                     {:op :log-production-record :production-id "unknown-production"
                      :patch {:scene "1A"}}
                     ctx)
      (is (= 0 (count (store/coordination-log db)))
          "HARD hold must never commit"))))

(deftest unverified-production-hard-hold
  (testing "registered but unverified production -> permanent HARD hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-4" :phase 3}
          result (exec-request actor "t4"
                               {:op :log-production-record :production-id "production-3"
                                :patch {:scene "1A"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "unverified production must HARD hold"))))

(deftest effect-not-propose-hard-hold
  (testing "proposal with :effect :commit (not :propose) -> hard hold"
    (let [db (store/seed-db)
          bad-advisor (reify advisor/Advisor
                        (-advise [_ _ req]
                          (assoc (advisor/infer nil req) :effect :commit)))
          actor (op/build db {:advisor bad-advisor})
          ctx {:actor-id "test-5" :phase 3}
          result (exec-request actor "t5"
                               {:op :log-production-record :production-id "production-1"
                                :patch {:scene "1A"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "non-:propose effect must HARD hold"))))

(deftest scope-excluded-content-hard-hold
  (testing "proposal drifting into safety-clearance-finalization/minor-performer-hour-override scope -> permanent hard hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-6" :phase 3}
          result (exec-request actor "t6"
                               {:op :log-production-record :production-id "production-1"
                                :out-of-scope? true  ; triggers scope pollution in advisor
                                :patch {}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "scope-excluded content must HARD hold"))))

(deftest social-distribution-handoff-full-flow
  (testing "clean social/platform distribution handoff proposal -> auto-commit at phase 3"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-9" :phase 3}
          result (exec-request actor "t9"
                               {:op :coordinate-social-distribution-handoff :production-id "production-1"
                                :patch {:platform "youtube-shorts" :destination "channel-a"}}
                               ctx)]
      (is (some? result))
      (is (> (count (store/coordination-log db)) 0)
          "clean social-distribution-handoff proposal must auto-commit at phase 3"))))

(deftest platform-content-policy-concern-always-escalates
  (testing ":flag-platform-content-policy-concern escalates for human approval, regardless of phase/confidence"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-10" :phase 3}
          result (exec-request actor "t10"
                               {:op :flag-platform-content-policy-concern :production-id "production-1"
                                :patch {:concern "possible community-guideline risk in the trailer cut" :confidence 0.99}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "platform content-policy concern must not auto-commit, must wait for approval")
      (resume-approval actor "t10" :approved)
      (is (> (count (store/coordination-log db)) 0)
          "after approval, record must be committed"))))

(deftest platform-posting-finalization-hard-hold
  (testing "proposal that finalizes/executes an actual platform posting -> permanent hard hold"
    (let [db (store/seed-db)
          bad-advisor (reify advisor/Advisor
                        (-advise [_ _ req]
                          (assoc (advisor/infer nil req)
                                 :rationale "finalize the platform posting for the trailer cut and go live")))
          actor (op/build db {:advisor bad-advisor})
          ctx {:actor-id "test-11" :phase 3}
          result (exec-request actor "t11"
                               {:op :coordinate-social-distribution-handoff :production-id "production-1"
                                :patch {:platform "youtube-shorts"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "platform-posting-finalization content must HARD hold"))))

(deftest content-moderation-ruling-finalization-hard-hold
  (testing "proposal that finalizes a content-moderation ruling -> permanent hard hold"
    (let [db (store/seed-db)
          bad-advisor (reify advisor/Advisor
                        (-advise [_ _ req]
                          (assoc (advisor/infer nil req)
                                 :rationale "finalize the content moderation ruling as compliant")))
          actor (op/build db {:advisor bad-advisor})
          ctx {:actor-id "test-12" :phase 3}
          result (exec-request actor "t12"
                               {:op :flag-platform-content-policy-concern :production-id "production-1"
                                :patch {:concern "possible copyright risk"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "content-moderation-ruling-finalization content must HARD hold"))))

(deftest monetization-eligibility-finalization-hard-hold
  (testing "proposal that finalizes monetization eligibility -> permanent hard hold"
    (let [db (store/seed-db)
          bad-advisor (reify advisor/Advisor
                        (-advise [_ _ req]
                          (assoc (advisor/infer nil req)
                                 :summary "finalize monetization eligibility for the channel before handoff")))
          actor (op/build db {:advisor bad-advisor})
          ctx {:actor-id "test-13" :phase 3}
          result (exec-request actor "t13"
                               {:op :coordinate-social-distribution-handoff :production-id "production-1"
                                :patch {:platform "youtube-shorts"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "monetization-eligibility-finalization content must HARD hold"))))

(deftest ai-disclosure-waiver-hard-hold
  (testing "proposal that waives the AI-generated content disclosure requirement -> permanent hard hold"
    (let [db (store/seed-db)
          bad-advisor (reify advisor/Advisor
                        (-advise [_ _ req]
                          (assoc (advisor/infer nil req)
                                 :rationale "waive the ai-generated content disclosure requirement for this upload")))
          actor (op/build db {:advisor bad-advisor})
          ctx {:actor-id "test-14" :phase 3}
          result (exec-request actor "t14"
                               {:op :flag-platform-content-policy-concern :production-id "production-1"
                                :patch {:concern "AI-generated content disclosure question"}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/coordination-log db)))
          "ai-disclosure-waiver content must HARD hold"))))

(deftest phase-1-approval-gate
  (testing "phase 1 approved request -> commits after human approval"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-7" :phase 1}]
      (exec-request actor "t7"
                     {:op :log-production-record :production-id "production-1"
                      :patch {:scene "1A"}}
                     ctx)
      (is (= 0 (count (store/coordination-log db)))
          "phase 1 must not auto-commit, requires approval")
      (resume-approval actor "t7" :approved)
      (is (> (count (store/coordination-log db)) 0)
          "after approval, must commit")
      (is (some #(= :committed (:t %)) (store/ledger db))
          "committed fact must be logged after approval"))))

(deftest audit-trail-completeness
  (testing "every decision leaves immutable audit facts"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-8" :phase 3}]
      (exec-request actor "t8a"
                     {:op :log-production-record :production-id "production-1" :patch {:scene "1A"}}
                     ctx)
      (exec-request actor "t8b"
                     {:op :log-production-record :production-id "unknown" :patch {:scene "1A"}}
                     ctx)
      (let [ledger (store/ledger db)]
        (is (> (count ledger) 0))
        (is (some #(= :committed (:t %)) ledger)
            "successful commits must be logged")
        (is (some #(= :governor-hold (:t %)) ledger)
            "HARD holds must be logged")))))
