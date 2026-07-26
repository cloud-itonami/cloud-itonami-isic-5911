(ns filmprodops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`filmprodops.operation` -> `filmprodops.governor` -> `filmprodops.
  store`) through a scenario cross-checked against this repo's own
  `filmprodops.sim` demo driver (`clojure -M:dev:run`, confirmed by
  actually running it before this file was written -- unlike
  `cloud-itonami-isic-851`'s `schoolops.sim`, this repo's own sim driver
  uses ids that DO match `filmprodops.store/demo-data`'s seeded
  productions exactly, and every disposition it produces (auto-commit /
  escalate+approve / HARD hold, and the exact `:rule` on each hold)
  matches `filmprodops.governor`'s own documented checks precisely, so
  it was safe to reuse as a starting point rather than author from
  scratch), trimmed to a representative subset (three distinct phase-3
  auto-commit ops, the always-escalate on-set-safety-concern flag
  lifecycle with a human approval, and three distinct HARD-hold reasons
  that never reach a human) and rendered deterministically -- no
  invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed (verified by diffing two
  consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [filmprodops.store :as store]
            [filmprodops.operation :as op]
            [filmprodops.advisor :as advisor]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :production-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real production ids from
  `filmprodops.store/demo-data`:

  production-1 (Neon Harbor, registered+verified) and production-2
  (Late Bloom, registered+verified) walk the clean phase-3 lifecycle:
  `:log-production-record` and `:schedule-production-operation` on
  production-1, and `:coordinate-post-production-handoff` on
  production-2 -- all three are members of phase 3's `:auto` set AND
  governor-clean, so all three auto-commit with no human in the loop.
  `:flag-onset-safety-concern` on production-1 -- the ONE op this actor
  NEVER auto-commits at any phase (`filmprodops.governor`'s own
  `always-escalate-ops` AND `filmprodops.phase`'s own tables agree,
  independently) -- ALWAYS escalates and is approved by a human
  production coordinator.

  Then three DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation):
    - production-99 (does not exist in the seed data at all):
      `:log-production-record` HARD-holds on `:production-unverified`
      -- the governor never trusts a proposal's own `:production-id`
      claim without an independent store lookup.
    - production-1, advisor attempts direct actuation (`:effect
      :commit` instead of `:effect :propose`): `:schedule-production-
      operation` HARD-holds on `:effect-not-propose` -- any effect
      other than `:propose` is, by construction, a claim to actuate
      outside governance.
    - production-1, advisor drifts into the permanently-excluded
      safety-clearance-finalization/minor-performer-hour-override
      scope (`:out-of-scope? true`): `:log-production-record`
      HARD-holds on `:scope-excluded` -- this actor's charter
      structurally excludes that territory, evaluated unconditionally
      on every proposal regardless of op or confidence.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; production-1: clean shoot-day/scene/take record log -- phase-3
    ;; auto-commit, clean.
    (exec! actor "p1-log" {:op :log-production-record :production-id "production-1"
                            :patch {:scene "12B" :take 1 :status "print"}})

    ;; production-1: clean shoot-day/location/crew scheduling proposal
    ;; -- phase-3 auto-commit, clean.
    (exec! actor "p1-schedule" {:op :schedule-production-operation :production-id "production-1"
                                 :patch {:location "rooftop lot 4" :date "2026-07-20" :crew-call "06:00"}})

    ;; production-2: clean footage/dailies post-production handoff
    ;; coordination -- phase-3 auto-commit, clean.
    (exec! actor "p2-handoff" {:op :coordinate-post-production-handoff :production-id "production-2"
                                :patch {:dailies "day-3" :destination "edit-bay-1"}})

    ;; production-1: on-set safety concern flag -- ALWAYS escalates,
    ;; approved by a human production coordinator.
    (exec! actor "p1-safety" {:op :flag-onset-safety-concern :production-id "production-1"
                               :patch {:concern "stunt coordinator flagged elevated risk on rooftop chase sequence"
                                       :confidence 0.92}})
    (approve! actor "p1-safety")

    ;; production-99 (does not exist in the seed data at all) ->
    ;; HARD hold on :production-unverified, never reaches a human.
    (exec! actor "p99-log" {:op :log-production-record :production-id "production-99"
                             :patch {:scene "1A" :take 1}})

    ;; production-1, advisor attempts direct actuation (:effect :commit
    ;; instead of :effect :propose) -> HARD hold on
    ;; :effect-not-propose, never reaches a human.
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "p1-direct" {:op :schedule-production-operation :production-id "production-1"
                                        :patch {:location "soundstage 2" :date "2026-07-22"}}))

    ;; production-1, advisor drifts into the permanently-excluded
    ;; safety-clearance-finalization/minor-performer-hour-override
    ;; scope -> HARD hold on :scope-excluded, never reaches a human.
    (exec! actor "p1-outofscope" {:op :log-production-record :production-id "production-1"
                                   :out-of-scope? true
                                   :patch {}})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger production-id]
  (last (filter #(= (:production-id %) production-id) ledger)))

(defn- status-cell [ledger production-id]
  (let [f (last-fact-for ledger production-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- production-row [ledger {:keys [production-id title registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc production-id) (esc title)
          (if registered? "<span class=\"ok\">registered</span>" "<span class=\"critical\">unregistered</span>")
          (if verified? "<span class=\"ok\">verified</span>" "<span class=\"critical\">unverified</span>")
          (status-cell ledger production-id)))

(defn- ledger-row [{:keys [t op production-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc production-id)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- record-row [{:keys [op production-id value payload]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (name op)) (esc production-id)
          (esc (str/join ", " (map name (keys (or value {})))))
          (if (:approved-by payload) (str "<span class=\"warn\">approved by " (esc (:approved-by payload)) "</span>")
              "<span class=\"ok\">auto-commit</span>")))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`filmprodops.governor`/`filmprodops.phase`) -- documentation of
  ;; fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-production-record</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; shoot-day/scene/take record logging only</span></td></tr>"
   "        <tr><td><code>:schedule-production-operation</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; shoot-day/location/crew scheduling PROPOSAL only, never a binding call sheet</span></td></tr>"
   "        <tr><td><code>:coordinate-post-production-handoff</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; footage/dailies handoff coordination only</span></td></tr>"
   "        <tr><td><code>:flag-onset-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval, at every rollout phase &middot; never auto-committed regardless of confidence</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        productions (store/all-productions db)
        production-rows (str/join "\n" (map (partial production-row ledger) productions))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        record-rows (str/join "\n" (map record-row (store/coordination-log db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-5911 &middot; motion picture, video and television programme production activities</title><style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Motion picture, video and television programme production activities (ISIC 5911) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · on-set safety concern flags always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Productions</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>filmprodops.store</code> via <code>filmprodops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Production</th><th>Title</th><th>Registered</th><th>Verified</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     production-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination records (this run)</h2>\n"
     "    <p class=\"muted\">Every write that reached the SSoT this run — auto-committed at phase 3, or human-approved after an always-escalate op.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Production</th><th>Fields</th><th>Disposition</th></tr></thead>\n"
     "      <tbody>\n"
     record-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Film Production Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Production registration/verification, <code>:effect</code> integrity, and the permanent on-set-safety-clearance/minor-performer-hour/rigging-actuation/talent-compensation/insurance-legal-union scope exclusion are independently recomputed, never trusted from the advisor's proposal; an on-set safety concern flag is always a human production coordinator's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Production</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
