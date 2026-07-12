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
  through a master GainNode. play! is tolerant: an unknown sound id or
  a buffer that hasn't finished fetching/decoding is a silent no-op.")

(def sound-files
  "sound id -> audio file base path (extension chosen at fetch time)"
  {:shell-fire "audio/trprsht1"})

(defonce audio-state
  (atom {:context nil
         :master-gain nil
         :raw-data {}
         :buffers {}}))

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

(defn preload!
  "Kick off background fetches for every sound file. Safe to call at
  page load; failures are ignored (play! just no-ops)."
  []
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
  "Play a sound by id. No-ops silently if the context doesn't exist
  yet, the id is unknown, or the buffer hasn't finished loading."
  [sound-id]
  (let [{:keys [context master-gain buffers]} @audio-state]
    (when-let [buffer (get buffers sound-id)]
      (let [source (.createBufferSource context)]
        (set! (.-buffer source) buffer)
        (.connect source master-gain)
        (.start source)))))
