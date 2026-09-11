(ns filmprodops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean production-record
  logging request through intake -> advise -> govern -> decide ->
  approval -> commit at phase 1 (assisted-logging, always approval),
  then re-runs the same op at phase 3 (supervised-auto, clean + high
  confidence -> auto-commit), then a shoot-day/location/crew scheduling
  request, a post-production handoff coordination (both auto-commit
  clean at phase 3), then an on-set safety concern flag (ALWAYS
  escalates, at any phase -- approve, then commit), then HARD-hold
  scenarios: an unregistered production, a production registered but
  not yet verified, a proposal whose own `:effect` is not `:propose`,
  and a proposal that has drifted into the permanently-excluded
  safety-clearance-finalization/minor-performer-hour-override scope."
  (:require [langgraph.graph :as g]
            [filmprodops.advisor :as advisor]
            [filmprodops.store :as store]
            [filmprodops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "production-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :production-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :production-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-production-record production-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-production-record :production-id "production-1"
                                  :patch {:scene "12A" :take 3 :status "print"}} coordinator-phase-1)]
      (println r)
      (println "-- human production coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-production-record production-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-production-record :production-id "production-1"
                                  :patch {:scene "12B" :take 1 :status "print"}} coordinator-phase-3))

    (println "\n== schedule-production-operation production-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-production-operation :production-id "production-1"
                                  :patch {:location "rooftop lot 4" :date "2026-07-20" :crew-call "06:00"}} coordinator-phase-3))

    (println "\n== coordinate-post-production-handoff production-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-post-production-handoff :production-id "production-1"
                                  :patch {:dailies "day-14" :destination "edit-bay-2"}} coordinator-phase-3))

    (println "\n== flag-onset-safety-concern production-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :flag-onset-safety-concern :production-id "production-1"
                                 :patch {:concern "stunt coordinator flagged elevated risk on rooftop chase sequence" :confidence 0.92}} coordinator-phase-3)]
      (println r)
      (println "-- human safety officer reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== log-production-record production-99 (unregistered production -> HARD hold) ==")
    (println (exec-op actor "t6" {:op :log-production-record :production-id "production-99"
                                  :patch {:scene "1A" :take 1}} coordinator-phase-3))

    (println "\n== log-production-record production-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-production-record :production-id "production-3"
                                  :patch {:scene "3C" :take 2}} coordinator-phase-3))

    (println "\n== schedule-production-operation production-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t8" {:op :schedule-production-operation :production-id "production-1"
                                           :patch {:location "soundstage 2" :date "2026-07-22"}} coordinator-phase-3)))

    (println "\n== log-production-record production-1, advisor drifts into safety-clearance-finalization/minor-performer-hour-override scope -> HARD hold, permanent ==")
    (println (exec-op actor "t9" {:op :log-production-record :production-id "production-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
