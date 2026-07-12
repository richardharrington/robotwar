(ns robotwar.audio
  "Web Audio API sound pipeline.

  Browser autoplay policy blocks (or suspends) AudioContexts created
  before a user gesture, so the context is constructed lazily by
  ensure-audio!, which app.cljs calls from the battle-start keydown
  handler — the first gesture a player can make.

  Sound files are fetched eagerly at page load (preload!) into raw
  ArrayBuffers; they are decoded into AudioBuffers as soon as the
  context exists. Each playback builds a fresh AudioBufferSourceNode
  (cheap, garbage-collected when finished, unlimited polyphony) routed
  through a per-play GainNode (volume from sound-volumes) into a master
  GainNode. play! is tolerant: an unknown sound id or a buffer that
  hasn't finished fetching/decoding is a silent no-op.

  The collision / wall-crash / robot-death sounds are synthesized with
  an OfflineAudioContext at page load (no gesture needed — offline
  contexts are exempt from autoplay policy) and cached as AudioBuffers,
  so playback treats them identically to file-based sounds.

  A sound on/off toggle lives here too (:enabled? in audio-state),
  persisted to localStorage. play! short-circuits when it's off.")

(def sound-files
  "sound id -> audio file base path (extension chosen at fetch time)"
  {:shell-fire "audio/trprsht1"
   :shell-explosion "audio/concuss5"})

(def sound-volumes
  "per-sound playback gain, applied via a per-play GainNode.
  Rough mix: explosions and deaths loud, contact thuds quiet."
  {:shell-fire 0.7
   :shell-explosion 1.0
   :robot-collision 0.5
   :wall-crash 0.45
   :robot-death 1.0})

(def ^:private enabled-storage-key "robotwar-sound-enabled")

(defonce audio-state
  (atom {:context nil
         :master-gain nil
         :raw-data {}
         :buffers {}
         :enabled? true}))

(defn- preferred-ext []
  (if (not= "" (.canPlayType (js/Audio.) "audio/ogg")) ".ogg" ".mp3"))

(defn- decode!
  "Decode a fetched ArrayBuffer into an AudioBuffer once the context
  exists. Drops the raw data afterward — decodeAudioData detaches the
  ArrayBuffer, so it must never be decoded twice."
  [sound-id array-buffer]
  (when-let [ctx (:context @audio-state)]
    (swap! audio-state update :raw-data dissoc sound-id)
    (-> (.decodeAudioData ctx array-buffer)
        (.then (fn [buffer]
                 (swap! audio-state assoc-in [:buffers sound-id] buffer)))
        (.catch (fn [_] nil)))))

;; ---------------------------------------------------------------------
;; Synthesized sound effects
;;
;; All three contact/death sounds are procedural (no audio assets).
;; :robot-collision and :wall-crash are cousins from the same "thud"
;; recipe — a lowpass-filtered noise burst plus a decaying sine pitch
;; drop — distinguished by filter cutoff, pitch, and length (collision
;; is short and knocky, wall crash is longer and duller). :robot-death
;; layers a metallic clang (inharmonic partials), a bright-to-dark
;; swept noise burst, a deep sine pitch drop, and a long low rumble.

(defn- fill-noise! [buffer]
  (let [data (.getChannelData buffer 0)
        n (.-length data)]
    (dotimes [i n]
      (aset data i (- (* 2 (js/Math.random)) 1)))))

(defn- add-noise-burst!
  "White noise -> lowpass (optionally swept) -> exponentially decaying
  gain -> destination."
  [ctx {:keys [duration cutoff-start cutoff-end gain]}]
  (let [sample-rate (.-sampleRate ctx)
        buffer (.createBuffer ctx 1 (js/Math.ceil (* duration sample-rate)) sample-rate)
        source (.createBufferSource ctx)
        filter (.createBiquadFilter ctx)
        gain-node (.createGain ctx)]
    (fill-noise! buffer)
    (set! (.-buffer source) buffer)
    (set! (.-type filter) "lowpass")
    (.setValueAtTime (.-frequency filter) cutoff-start 0)
    (.exponentialRampToValueAtTime (.-frequency filter) (or cutoff-end cutoff-start) duration)
    (.setValueAtTime (.-gain gain-node) gain 0)
    (.exponentialRampToValueAtTime (.-gain gain-node) 0.001 duration)
    (.connect source filter)
    (.connect filter gain-node)
    (.connect gain-node (.-destination ctx))
    (.start source 0)))

(defn- add-sine-drop!
  "Sine oscillator sliding freq-start -> freq-end with an exponentially
  decaying gain — the 'weight' under a thud or explosion."
  [ctx {:keys [duration freq-start freq-end gain]}]
  (let [osc (.createOscillator ctx)
        gain-node (.createGain ctx)]
    (set! (.-type osc) "sine")
    (.setValueAtTime (.-frequency osc) freq-start 0)
    (.exponentialRampToValueAtTime (.-frequency osc) freq-end duration)
    (.setValueAtTime (.-gain gain-node) gain 0)
    (.exponentialRampToValueAtTime (.-gain gain-node) 0.001 duration)
    (.connect osc gain-node)
    (.connect gain-node (.-destination ctx))
    (.start osc 0)
    (.stop osc duration)))

(defn- add-metal-partials!
  "Short inharmonic sine partials — reads as a metallic clang."
  [ctx {:keys [freqs duration gain]}]
  (doseq [freq freqs]
    (add-sine-drop! ctx {:duration duration
                         :freq-start freq
                         :freq-end (* freq 0.85)
                         :gain gain})))

(defn- render-offline!
  "Render build-fn into an AudioBuffer via an OfflineAudioContext and
  cache it under sound-id, exactly like a decoded file."
  [sound-id duration build-fn]
  (when-let [Ctor (or (.-OfflineAudioContext js/window)
                      (.-webkitOfflineAudioContext js/window))]
    (let [sample-rate 44100
          ctx (Ctor. 1 (js/Math.ceil (* duration sample-rate)) sample-rate)]
      (build-fn ctx)
      (-> (.startRendering ctx)
          (.then (fn [buffer]
                   (swap! audio-state assoc-in [:buffers sound-id] buffer)))
          (.catch (fn [_] nil))))))

(defn- synthesize-sfx! []
  (render-offline! :robot-collision 0.22
    (fn [ctx]
      (add-noise-burst! ctx {:duration 0.18 :cutoff-start 700 :gain 0.7})
      (add-sine-drop! ctx {:duration 0.22 :freq-start 160 :freq-end 70 :gain 0.8})))
  (render-offline! :wall-crash 0.3
    (fn [ctx]
      (add-noise-burst! ctx {:duration 0.28 :cutoff-start 320 :gain 0.9})
      (add-sine-drop! ctx {:duration 0.3 :freq-start 100 :freq-end 45 :gain 0.8})))
  (render-offline! :robot-death 1.6
    (fn [ctx]
      (add-metal-partials! ctx {:freqs [523 1247 2861] :duration 0.5 :gain 0.2})
      (add-noise-burst! ctx {:duration 1.2 :cutoff-start 2400 :cutoff-end 90 :gain 0.8})
      (add-sine-drop! ctx {:duration 1.1 :freq-start 210 :freq-end 30 :gain 0.9})
      (add-noise-burst! ctx {:duration 1.6 :cutoff-start 120 :gain 0.6}))))

;; ---------------------------------------------------------------------
;; Sound on/off toggle (persisted)

(defn sound-enabled? []
  (:enabled? @audio-state))

(defn- read-stored-enabled []
  (try
    (not= "false" (.getItem js/localStorage enabled-storage-key))
    (catch :default _ true)))

(defn set-sound-enabled! [enabled?]
  (swap! audio-state assoc :enabled? enabled?)
  (try
    (.setItem js/localStorage enabled-storage-key (str enabled?))
    (catch :default _ nil))
  enabled?)

(defn toggle-sound! []
  (set-sound-enabled! (not (sound-enabled?))))

;; ---------------------------------------------------------------------

(defn preload!
  "Kick off background fetches for every sound file, synthesize the
  procedural effects, and restore the persisted toggle state. Safe to
  call at page load; failures are ignored (play! just no-ops)."
  []
  (swap! audio-state assoc :enabled? (read-stored-enabled))
  (synthesize-sfx!)
  (let [ext (preferred-ext)]
    (doseq [[sound-id base] sound-files]
      (-> (js/fetch (str base ext))
          (.then (fn [resp] (.arrayBuffer resp)))
          (.then (fn [array-buffer]
                   (swap! audio-state assoc-in [:raw-data sound-id] array-buffer)
                   (decode! sound-id array-buffer)))
          (.catch (fn [_] nil))))))

(defn ensure-audio!
  "Construct the AudioContext and master gain, and decode any sounds
  fetched so far. Must be called from a user-gesture handler the first
  time; subsequent calls are no-ops."
  []
  (when-not (:context @audio-state)
    (when-let [Ctor (or (.-AudioContext js/window)
                        (.-webkitAudioContext js/window))]
      (let [ctx (Ctor.)
            master-gain (.createGain ctx)]
        (.connect master-gain (.-destination ctx))
        (swap! audio-state assoc :context ctx :master-gain master-gain)
        (doseq [[sound-id array-buffer] (:raw-data @audio-state)]
          (decode! sound-id array-buffer))))))

(defn play!
  "Play a sound by id. No-ops silently if sound is toggled off, the
  context doesn't exist yet, the id is unknown, or the buffer hasn't
  finished loading."
  [sound-id]
  (let [{:keys [context master-gain buffers enabled?]} @audio-state]
    (when (and enabled? context)
      (when-let [buffer (get buffers sound-id)]
        (let [source (.createBufferSource context)
              gain-node (.createGain context)]
          (set! (.-buffer source) buffer)
          (set! (.. gain-node -gain -value) (get sound-volumes sound-id 1.0))
          (.connect source gain-node)
          (.connect gain-node master-gain)
          (.start source))))))
