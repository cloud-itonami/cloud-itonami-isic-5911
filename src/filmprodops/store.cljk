(ns filmprodops.store
  "SSoT for the ISIC-5911 motion-picture/video/TV-programme-production
  COORDINATION actor, behind a `Store` protocol so the backend is a swap,
  not a rewrite -- the same seam every `cloud-itonami-isic-*` actor in
  this fleet uses.

  This actor coordinates the back-office operations of a film/TV
  production: shoot-day/scene/take record logging, shoot-day/location/
  crew scheduling proposals, on-set safety-concern flagging (stunt-risk,
  hazard, talent-welfare), and footage/dailies handoff coordination to
  post-production. It never touches on-set safety-authority decisions
  (stunt-clearance finalization, minor-performer work-hour-limit
  overrides), direct equipment/rigging/pyrotechnic actuation, talent
  compensation/contract finalization, or insurance/legal/union
  adjudication -- see `filmprodops.governor`'s `scope-exclusion-violations`,
  a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `productions` directory keyed by `:production-id` STRING
  (never a keyword -- consistent keying from the start, avoiding the
  silent-miss bug that plagued an earlier shepherd attempt).

  A registered/verified production (or shoot-day) record must exist
  before ANY proposal for that production may ever commit or escalate --
  `filmprodops.governor`'s `production-unverified-violations` re-derives
  this from the production's own `:registered?`/`:verified?` fields,
  never from proposal self-report, the SAME 'ground truth, not
  self-report' discipline every sibling actor's own governor uses.

  The ledger stays append-only: which production a proposal targeted,
  which operation, on what basis, committed/held/escalated and approved
  by whom is always a query over an immutable log.")

(defprotocol Store
  (production [s production-id] "Registered production record, or nil.
    Production map: {:production-id .. :title .. :registered? bool :verified? bool}.")
  (all-productions [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-productions [s productions] "replace/seed the production directory (map production-id->production)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained production directory covering both the happy
  path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:productions
   {"production-1" {:production-id "production-1" :title "Neon Harbor (feature, principal photography)"
                     :registered? true :verified? true}
    "production-2" {:production-id "production-2" :title "Late Bloom (TV pilot, principal photography)"
                     :registered? true :verified? true}
    "production-3" {:production-id "production-3" :title "Skyline Runners (feature, insurance/COI pending)"
                     :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (production [_ production-id] (get-in @a [:productions production-id]))
  (all-productions [_] (sort-by :production-id (vals (:productions @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-productions [s productions] (when (seq productions) (swap! a assoc :productions productions)) s))

(defn seed-db
  "A MemStore seeded with the demo production directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `productions` map (production-id
  string -> production map) -- the primary test/dev entry point.
  `productions` may be empty (an unregistered-everywhere store)."
  [productions]
  (->MemStore (atom {:productions (or productions {}) :ledger [] :coordination-log []})))
