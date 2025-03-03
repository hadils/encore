(ns ^:no-doc taoensso.encore.stats
  "Private stats utils.
  Experimental, subject to change without notice!"
  {:added "Encore v3.98.0 (2024-04-08)"}
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc  :refer [have have? have!]]
   #?(:cljs [goog.array]))

  #?(:clj (:import [java.util LinkedList])))

(comment
  (remove-ns 'taoensso.encore.stats)
  (:api (enc/interns-overview)))

;; TODO Add `SummaryStatsRolling` (rolling by max len and/or date)?

;;;;

#?(:clj (let [c (Class/forName "[J")] (defn longs?   "Returns true iff given array of longs"   [x] (instance? c x))))
#?(:clj (let [c (Class/forName "[D")] (defn doubles? "Returns true iff given array of doubles" [x] (instance? c x))))

;;;; Sorted nums

(deftype SortedLongs [^longs a]
  #?@(:clj
      [Object               (toString [x] (enc/str-impl x "taoensso.encore.stats.SortedLongs" {:length (alength a)}))
       clojure.lang.Counted (count    [_] (alength a))
       clojure.lang.Indexed
       (nth [_ idx          ] (aget a idx))
       (nth [_ idx not-found]
         (let [max-idx (dec (alength a))]
           (enc/cond
             (> idx max-idx) not-found
             (< idx max-idx) not-found
             :else (aget a idx))))

       clojure.lang.IReduceInit
       (reduce [_ f init]
         #_(areduce a i acc init (f acc (aget a i)))
         (reduce (fn [acc idx]   (f acc (aget a idx)))
           init (range (alength a))))]

      :cljs
      [Object   (toString [x] (enc/str-impl x "taoensso.encore.stats.SortedLongs" {:length (alength a)}))
       ICounted (-count   [_] (alength a))
       IIndexed
       (-nth [_ idx          ] (aget a idx))
       (-nth [_ idx not-found]
         (let [max-idx (dec (alength a))]
           (enc/cond
             (> idx max-idx) not-found
             (< idx max-idx) not-found
             :else (aget a idx))))

       IReduce
       (-reduce [_ f init]
         #_(areduce a i acc init (f acc (aget a i)))
         (reduce (fn [acc i]     (f acc (aget a i)))
           init (range (alength a))))]))

(deftype SortedDoubles [^doubles a]
  #?@(:clj
      [Object               (toString [x] (enc/str-impl x "taoensso.encore.stats.SortedDoubles" {:length (alength a)}))
       clojure.lang.Counted (count    [_] (alength a))
       clojure.lang.Indexed
       (nth [_ idx          ] (aget a idx))
       (nth [_ idx not-found]
         (let [max-idx (dec (alength a))]
           (enc/cond
             (> idx max-idx) not-found
             (< idx max-idx) not-found
             :else (aget a idx))))

       clojure.lang.IReduceInit
       (reduce [_ f init]
         #_(areduce a i acc init (f acc (aget a i)))
         (reduce (fn [acc idx]   (f acc (aget a idx)))
           init (range (alength a))))]

      :cljs
      [Object   (toString [x] (enc/str-impl x "taoensso.encore.stats.SortedDoubles" {:length (alength a)}))
       ICounted (-count   [_] (alength a))
       IIndexed
       (-nth [_ idx          ] (aget a idx))
       (-nth [_ idx not-found]
         (let [max-idx (dec (alength a))]
           (enc/cond
             (> idx max-idx) not-found
             (< idx max-idx) not-found
             :else (aget a idx))))

       IReduce
       (-reduce [_ f init]
         #_(areduce a i acc init (f acc (aget a i)))
         (reduce (fn [acc i]     (f acc (aget a i)))
           init (range (alength a))))]))

(defn sorted-longs?   [x] (instance? SortedLongs   x))
(defn sorted-doubles? [x] (instance? SortedDoubles x))
(defn sorted-nums?    [x] (or (sorted-longs? x) (sorted-doubles? x)))

(defn sorted-longs
  "Returns `SortedLongs` given `SortedLongs`, `SortedDoubles`, or num seq."
  (^SortedLongs [                nums] (sorted-longs false nums))
  (^SortedLongs [allow-mutation? nums]
   (enc/cond
     (sorted-longs?   nums)                                               nums
     (sorted-doubles? nums) (SortedLongs. (long-array (.-a ^SortedDoubles nums)))
     :else
     #?(:clj
        (let [^longs a (if (longs? nums) (if allow-mutation? nums (aclone ^longs nums)) (long-array nums))]
          (java.util.Arrays/sort a) ; O(n.log(n)) on JDK 7+
          (SortedLongs.          a))

        :cljs
        (let [a (if (array? nums) (if allow-mutation? nums (aclone nums)) (to-array nums))]
          (goog.array/sort a)
          (SortedLongs.    a))))))

(defn sorted-doubles
  "Returns `SortedDoubles` given `SortedDoubles`, `SortedLongs`, or num seq."
  (^SortedDoubles [                nums] (sorted-doubles false nums))
  (^SortedDoubles [allow-mutation? nums]
   (enc/cond
     (sorted-doubles? nums)                                                 nums
     (sorted-longs?   nums) (SortedDoubles. (double-array (.-a ^SortedLongs nums)))
     :else
     #?(:clj
        (let [^doubles a (if (doubles? nums) (if allow-mutation? nums (aclone ^doubles nums)) (double-array nums))]
          (java.util.Arrays/sort a) ; O(n.log_n) on JDK 7+
          (SortedDoubles.        a))

        :cljs
        (let [a (if (array? nums) (if allow-mutation? nums (aclone nums)) (to-array nums))]
          (goog.array/sort a)
          (SortedDoubles.  a))))))

(comment (into [] (sorted-doubles (sorted-longs [3 1 2]))))

(defn- fast-first [x]
  #?(:cljs (nth x 0 nil)
     :clj
     (if (instance? LinkedList x)
       (.peekFirst ^LinkedList x)
       (nth x 0 nil))))

(defn sorted-nums
  "Returns `SortedLongs` or `SortedDoubles`,
  given `SortedLongs`, `SortedDoubles`, or num seq."
  ([                nums] (sorted-nums false nums))
  ([allow-mutation? nums]
   [nums]
   (enc/cond
     (sorted-longs?   nums) nums
     (sorted-doubles? nums) nums
     (when-let [n1 (fast-first nums)] (enc/float? n1)) (sorted-doubles allow-mutation? nums)
     :else                                             (sorted-longs   allow-mutation? nums))))

(comment (sorted-nums [1.0 2]))

;;;; Percentiles

(defn- weighted-nth
  "Returns interpolated nth numerical value."
  [idx nums]
  (let [idx       (double     idx)
        idx-floor (Math/floor idx)
        idx-ceil  (Math/ceil  idx)]

    (if (== idx-ceil idx-floor)
      (nth nums (int idx))

      ;; Generalization of (floor+ceil)/2
      (let [weight-floor (- idx-ceil idx)
            weight-ceil  (- 1 weight-floor)]
        (+
          (* weight-floor (double (nth nums (int idx-floor))))
          (* weight-ceil  (double (nth nums (int idx-ceil)))))))))

(comment (weighted-nth 0.5 [1 5]))

(defn percentile
  "Returns ?element."
  [pnum nums]
  (let [snums   (sorted-nums nums)
        max-idx (dec (count snums))]
    (when (>= max-idx 0)
      (nth snums (Math/round (* max-idx (enc/as-pnum! pnum)))))))

(comment (percentile 0.8 (range 101)))

(defn percentiles
  "Returns ?[min p25 p50 p75 p90 p95 p99 max] elements in:
    - O(1) for Sorted types (SortedLongs, SortedDoubles),
    - O(n.log_n) otherwise."
  [nums]
  (let [snums   (sorted-nums nums)
        max-idx (dec (count snums))]
    (when (>= max-idx 0)
      [(nth snums 0)
       (nth snums (Math/round (* max-idx 0.25)))
       (nth snums (Math/round (* max-idx 0.50)))
       (nth snums (Math/round (* max-idx 0.75)))
       (nth snums (Math/round (* max-idx 0.90)))
       (nth snums (Math/round (* max-idx 0.95)))
       (nth snums (Math/round (* max-idx 0.99)))
       (nth snums                max-idx)])))

(comment
  (percentiles (range 101))
  (percentiles [1 2 3]))

;;;;

(defn bessel-correction ^double [n ^double add] (+ (double n) add))

(defn rf-sum          ^double [^double acc ^double in] (+ acc in))
(defn rf-sum-variance ^double [^double xbar ^double acc x]
  (+ acc (Math/pow (- (double x) xbar) 2.0)))

(defn rf-sum-abs-deviation ^double [^double central-point ^double acc x]
  (+ acc (Math/abs (- (double x) central-point))))

;;;; SummaryStats

(declare ^:private deref-sstats)
(deftype SummaryStats
  ;; - Field names chosen to avoid shadowing.
  ;; - Includes -sum data to support merging.
  ;; - Doubles used as general type to avoid boxing.
  [^boolean xfloats?
   ^long    nx
   ^double  xsum
   ^double  xmin
   ^double  xmax
   ^double  xlast
   ^double  p25
   ^double  p50
   ^double  p75
   ^double  p90
   ^double  p95
   ^double  p99
   ;;       xmean
   ;;       xvar
   ;;       xmad
   ^double  xvar-sum
   ^double  xmad-sum]

  Object (toString [x] (enc/str-impl x "taoensso.encore.stats.SummaryStats" {:n nx}))
  #?@(:clj  [clojure.lang.IDeref ( deref [this] (deref-sstats this))]
      :cljs [             IDeref (-deref [this] (deref-sstats this))]))

(defn- deref-sstats [^SummaryStats ss]
  (let [fin (if (.-xfloats? ss) double #(Math/round (double %)))
        nx  (.-nx ss)]
    (assert (pos? nx))
    (with-meta
      {:n           nx
       :sum     (fin (.-xsum  ss))
       :min     (fin (.-xmin  ss))
       :max     (fin (.-xmax  ss))
       :last    (fin (.-xlast ss))
       :p25     (fin (.-p25   ss))
       :p50     (fin (.-p50   ss))
       :p75     (fin (.-p75   ss))
       :p90     (fin (.-p90   ss))
       :p95     (fin (.-p95   ss))
       :p99     (fin (.-p99   ss))

       :mean    (/ (.-xsum     ss) nx)
       :var     (/ (.-xvar-sum ss) nx) ; Currently w/o bessel-correction
       :mad     (/ (.-xmad-sum ss) nx)

       :var-sum (.-xvar-sum ss)
       :mad-sum (.-xmad-sum ss)}

      {:floats? (.-xfloats? ss)})))

(defn ^:public summary-stats?
  "Returns true iff given a `SummaryStats` argument."
  [x] (instance? SummaryStats x))

(defn ^:public summary-stats
  "Given a coll of numbers or previously dereffed `SummaryStats` map,
  returns a new mergeable ?`SummaryStats` with:
    (deref ss) => {:keys [n sum min max p25 ... p99 mean var mad]}

  See also `summary-stats-merge`."
  {:arglists '([nums-or-ss-map])}
  ([     x] (summary-stats nil x))
  ([opts x]
   (when x
     (enc/cond
       (summary-stats? x) x
       (map?           x)
       (let [{:keys [n sum min max last p25 p50 p75 p90 p95 p99
                     #_mean #_var #_mad var-sum mad-sum]} x

             floats?
             (enc/cond
               :if-let [e (find opts     :floats?)] (val e)
               :if-let [e (find (meta x) :floats?)] (val e)
               :else (enc/float? sum))]

         (SummaryStats. floats?
           n sum min max last p25 p50 p75 p90 p95 p99 var-sum mad-sum))

       :else
       (let [snums
             (if-let [e (find opts :floats?)]
               (if (val e)
                 (sorted-doubles true x)
                 (sorted-longs   true x))
               (sorted-nums      true x))

             nx (count snums)]

         (when (pos? nx)
           (let [[xmin p25 p50 p75 p90 p95 p99 xmax] (percentiles snums)
                 xsum  (double (reduce rf-sum 0.0 snums))
                 xbar  (/ xsum nx)
                 xlast (nth snums (dec nx))
                 [^double xvar-sum ^double xmad-sum]
                 (enc/reduce-multi
                   (partial rf-sum-variance      xbar) 0.0
                   (partial rf-sum-abs-deviation xbar) 0.0
                   snums)]

             (SummaryStats. (sorted-doubles? snums)
               nx xsum xmin xmax xlast p25 p50 p75 p90 p95 p99
               xvar-sum xmad-sum))))))))

(comment @(summary-stats [1 2 3]))

(defn ^:public summary-stats-merge
  "Given one or more ?`SummaryStats`, returns a new ?`SummaryStats` with:
    (summary-stats-merge
       (summary-stats nums1)
       (summary-stats nums2))

    an approximatation of (summary-stats (merge nums1 nums2))

  Useful when you want summary stats for a large coll of numbers for which
  it would be infeasible/expensive to keep all numbers for accurate merging."
  ([ss1    ] ss1)
  ([ss1 ss2]
   (if ss1
     (if ss2
       (let [^SummaryStats ss1 ss1
             ^SummaryStats ss2 ss2

             nx1 (.-nx ss1)
             nx2 (.-nx ss2)

             _ (assert (pos? nx1))
             _ (assert (pos? nx2))

             xfloats1? (.-xfloats? ss1)
             xsum1     (.-xsum     ss1)
             xmin1     (.-xmin     ss1)
             xmax1     (.-xmax     ss1)
             p25-1     (.-p25      ss1)
             p50-1     (.-p50      ss1)
             p75-1     (.-p75      ss1)
             p90-1     (.-p90      ss1)
             p95-1     (.-p95      ss1)
             p99-1     (.-p99      ss1)
             xvar-sum1 (.-xvar-sum ss1)
             xmad-sum1 (.-xmad-sum ss1)

             xfloats2? (.-xfloats? ss2)
             xsum2     (.-xsum     ss2)
             xmin2     (.-xmin     ss2)
             xmax2     (.-xmax     ss2)
             xlast2    (.-xlast    ss2)
             p25-2     (.-p25      ss2)
             p50-2     (.-p50      ss2)
             p75-2     (.-p75      ss2)
             p90-2     (.-p90      ss2)
             p95-2     (.-p95      ss2)
             p99-2     (.-p99      ss2)
             xvar-sum2 (.-xvar-sum ss2)
             xmad-sum2 (.-xmad-sum ss2)

             xfloats3? (or xfloats1? xfloats2?)
             nx3       (+ nx1 nx2)
             nx1-ratio (/ (double nx1) (double nx3))
             nx2-ratio (/ (double nx2) (double nx3))

             xsum3 (+ xsum1 xsum2)
             xmin3 (if (< xmin1 xmin2) xmin1 xmin2)
             xmax3 (if (> xmax1 xmax2) xmax1 xmax2)
             ;; xbar3 (/ xsum3 nx3)

             ;; Batched "online" calculation here is better= the standard
             ;; Knuth/Welford method, Ref. http://goo.gl/QLSfOc,
             ;;                            http://goo.gl/mx5eSK.
             ;; No apparent advantage in using `xbar3` asap.
             xvar-sum3 (+ xvar-sum1 xvar-sum2)
             xmad-sum3 (+ xmad-sum1 xmad-sum2)

             ;; These are pretty rough approximations. More sophisticated
             ;; approaches not worth the extra cost/effort in our case.
             p25-3 (+ (* nx1-ratio p25-1) (* nx2-ratio p25-2))
             p50-3 (+ (* nx1-ratio p50-1) (* nx2-ratio p50-2))
             p75-3 (+ (* nx1-ratio p75-1) (* nx2-ratio p75-2))
             p90-3 (+ (* nx1-ratio p90-1) (* nx2-ratio p90-2))
             p95-3 (+ (* nx1-ratio p95-1) (* nx2-ratio p95-2))
             p99-3 (+ (* nx1-ratio p99-1) (* nx2-ratio p99-2))]

         (SummaryStats. xfloats3?
           nx3 xsum3 xmin3 xmax3 xlast2 p25-3 p50-3 p75-3 p90-3 p95-3 p99-3
           xvar-sum3 xmad-sum3))
       ss1)
     ss2)))

;;;; Stateful SummaryStats

(defn- buf-new
  ([    ] #?(:clj (LinkedList.) :cljs (array)))
  ([init]
   #?(:clj  (if init (LinkedList. init) (LinkedList.))
      :cljs (if init (array       init) (array)))))

(defn- buf-add [buf x]
  #?(:clj  (.add ^LinkedList buf x)
     :cljs (.push            buf x)))

(defn- buf-len ^long [buf]
  #?(:clj  (.size ^LinkedList buf)
     :cljs (alength           buf)))

(defprotocol ISummaryStatsBuffered
  ;; TODO Later generalize protocol for other stateful SummaryStats types?
  (ssb-deref [_] [_ flush?] "Returns current ?sstats.")
  (ssb-clear [_]   "Clears all internal state and returns nil.")
  (ssb-flush [_]   "Flushes internal buffer and returns newly merged sstats or nil.")
  (ssb-push  [_ n] "Adds given num to internal buffer."))

(defn ^:public summary-stats-clear!
  "Clears internal state (incl. stats and buffers, etc.) for given
  stateful `SummaryStats` instance and returns nil."
  [stateful-summary-stats]
  (ssb-clear stateful-summary-stats))

(deftype SummaryStatsBuffered [sstats_ buf_ buf-size merge-counter merge-cb]
  Object
  (toString [x]
    (enc/str-impl x "taoensso.encore.stats.SummaryStatsBuffered"
      {:n       (get (sstats_) :n 0)
       :pending (buf-len (buf_))
       :merged  (if-let [mc merge-counter] @mc 0)}))

  #?@(:clj  [clojure.lang.IDeref ( deref [this] (ssb-deref this))]
      :cljs [             IDeref (-deref [this] (ssb-deref this))])

  #?@(:clj  [clojure.lang.IFn ( invoke [this n] (ssb-push this n))]
      :cljs [             IFn (-invoke [this n] (ssb-push this n))])

  ISummaryStatsBuffered
  (ssb-deref [this       ] (ssb-deref this true))
  (ssb-deref [this flush?] (or (and flush? (ssb-flush this)) (sstats_)))
  (ssb-clear [_]
    #?(:clj (locking buf_ (reset! buf_ (buf-new)))
       :cljs              (reset! buf_ (buf-new)))

    (reset! sstats_ nil)
    (when-let [mc merge-counter] (mc :set 0))
    nil)

  (ssb-flush [this]
    (let [[drained]
          #?(:clj (locking buf_ (reset-vals! buf_ (buf-new nil)))
             :cljs              (reset-vals! buf_ (buf-new nil)))]

      (if (== (buf-len drained) 0)
        nil
        (let [t0             (when merge-cb (enc/now-nano*))
              _              (when-let [mc merge-counter] (mc))
              sstats-drained (summary-stats drained)

              sstats-merged ; Only drainer will update, so should be no contention
              (sstats_ (fn [old] (summary-stats-merge old sstats-drained)))]

          (when merge-cb ; Handy for profilers, etc.
            (merge-cb this (- (enc/now-nano*) ^long t0)))

          sstats-merged))))

  (ssb-push [this n]
    #?(:clj (locking buf_ (buf-add (buf_) n))
       :cljs              (buf-add (buf_) n))

    (when-let [^long nmax buf-size]
      (when (> (buf-len (buf_)) nmax)
        (ssb-flush this)))

    nil))

(defn ^:public summary-stats-buffered
  "Returns a new stateful `SummaryStatsBuffered` with:
    (ssb <num>) => Adds given number to internal buffer.
    (deref ssb) => Flushes buffer if necessary, and returns a mergeable
                   `?SummaryStats`. Deref again to get a map of summary
                   stats for all numbers ever added to ssb:
                     {:keys [n sum min max p25 ... p99 mean var mad]}.

  Useful for summarizing a (possibly infinite) stream of numbers.

  Options:
    `:buffer-init` - Initial buffer content, useful for persistent ssb.
    `:sstats-init` - Initial summary stats,  useful for persistent ssb.
    `:buffer-size`
       The maximum number of numbers that may be buffered before next
       (ssb <num>) call will block to flush buffer and merge with any
       existing summary stats.

       Larger buffers mean better performance and more accurate stats,
       at the cost of more memory use while buffering."

  ([] (summary-stats-buffered nil))
  ([{:keys [buffer-size buffer-init sstats-init merge-cb]
     :or   {buffer-size 1e5}
     :as   opts}]

   (SummaryStatsBuffered.
     (enc/latom (summary-stats sstats-init))
     (enc/latom       (buf-new buffer-init))
     (long                     buffer-size)
     (enc/counter)
     merge-cb ; Undocumented
     )))

(defn summary-stats-buffered-fast
  "Returns fastest possible `SummaryStatsBuffered`."
  [^long buffer-size merge-cb]
  (SummaryStatsBuffered.
    (enc/latom nil)
    (enc/latom (buf-new))
    buffer-size
    nil
    merge-cb))

(comment
  (let [ssb (summary-stats-buffered {:buffer-size 10})] ; 175 qb
    [(enc/qb 1e6 (ssb (rand-int 1000))) (str ssb) @@ssb]))

(defn summary-stats-buffered?
  "Returns true iff given a `SummaryStatsBuffered` instance."
  [x] (instance? SummaryStatsBuffered x))

(defn summary-stats-stateful?
  "Returns true iff given a stateful `SummaryStats` instance."
  [x] (summary-stats-buffered? x))

;;;; Print methods

(do
  (enc/def-print-impl [x SortedLongs]          (str "#" x))
  (enc/def-print-impl [x SortedDoubles]        (str "#" x))
  (enc/def-print-impl [x SummaryStats]         (str "#" x))
  (enc/def-print-impl [x SummaryStatsBuffered] (str "#" x)))
