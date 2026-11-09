package com.yesmin.ai.avatar

import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.tan
import com.google.android.filament.View as FilamentView

/**
 * The only file that talks to the Filament 3D engine (version 1.77.3, glTF 2.0 via gltfio).
 * Everything that decides WHAT to show lives in [AvatarController]; this class only draws it.
 *
 * Lifecycle: [attach] a TextureView, [ensureEngine], [loadModel], then [setVisible] controls the frame
 * loop. [destroy] releases every GPU object. All calls must be on the main thread.
 *
 * Facial expressions use glTF morph targets (blend shapes), so they only work on models that have them.
 * Clips use glTF skeletal animation. Head motion uses a head bone if one is found by name.
 */
class AvatarRenderer(
    private val config: AvatarConfiguration,
    private val brain: AvatarController
) {
    /** Called if drawing keeps failing, so the UI can fall back instead of showing a frozen frame. */
    var onFatalError: ((String) -> Unit)? = null

    private class MorphBinding(
        val instance: Int,
        val shapes: Array<FaceShape>,
        val indices: Array<IntArray>,
        val weights: FloatArray
    )

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: FilamentView? = null
    private var camera: Camera? = null
    private var cameraEntity = 0
    private var swapChain: SwapChain? = null
    private var uiHelper: UiHelper? = null
    private var materialProvider: MaterialProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var indirectLight: IndirectLight? = null
    private val lightEntities = ArrayList<Int>()

    private var surface: Surface? = null
    private var viewWidth = 0
    private var viewHeight = 0

    private var asset: FilamentAsset? = null
    private var animator: Animator? = null
    private var morphBindings: List<MorphBinding> = emptyList()
    private var headEntity = 0
    private val headBase = FloatArray(16)
    private var rootEntity = 0
    private val rootBase = FloatArray(16)
    private val pivot = FloatArray(3)
    private val scratchA = FloatArray(16)
    private val scratchB = FloatArray(16)
    private val scratchOut = FloatArray(16)

    // camera framing (world units)
    private var targetX = 0f
    private var targetY = 0f
    private var targetZ = 0f
    private var frameHeight = 1f

    private val choreographer = Choreographer.getInstance()
    private var visible = false
    private var running = false
    private var modelLoaded = false
    private var destroyed = false
    private var lastRenderNanos = 0L
    private var consecutiveErrors = 0

    val isEngineReady get() = engine != null

    // ------------------------------------------------------------------ surface

    /** Connect the view that will display the 3D scene. Replaces any previous one. */
    fun attach(textureView: TextureView) {
        detachSurface()
        val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        helper.isOpaque = false // transparent background so the app's blue/cyan environment shows through
        helper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(s: Surface) {
                surface = s
                rebuildSwapChain()
            }

            override fun onDetachedFromSurface() {
                destroySwapChain()
                surface = null
            }

            override fun onResized(width: Int, height: Int) {
                viewWidth = width
                viewHeight = height
                updateViewport()
            }
        }
        helper.attachTo(textureView)
        uiHelper = helper
    }

    fun detachSurface() {
        uiHelper?.let { runCatching { it.detach() } }
        uiHelper = null
        destroySwapChain()
        surface = null
    }

    private fun rebuildSwapChain() {
        val eng = engine ?: return
        val s = surface ?: return
        val helper = uiHelper ?: return
        swapChain?.let { eng.destroySwapChain(it) }
        swapChain = eng.createSwapChain(s, helper.swapChainFlags)
    }

    private fun destroySwapChain() {
        val eng = engine
        val sc = swapChain
        if (eng != null && sc != null) {
            eng.destroySwapChain(sc)
            eng.flushAndWait()
        }
        swapChain = null
    }

    // ------------------------------------------------------------------ engine

    /** Starts the 3D engine if it is not running. @return null on success, otherwise a readable error. */
    fun ensureEngine(): String? {
        if (destroyed) return "Renderer was closed."
        if (engine != null) return null
        return try {
            Filament.init()
            Gltfio.init()
            val eng = Engine.create()
            engine = eng
            renderer = eng.createRenderer().also {
                it.clearOptions = Renderer.ClearOptions().apply { clear = true }
            }
            scene = eng.createScene()
            cameraEntity = EntityManager.get().create()
            camera = eng.createCamera(cameraEntity)
            view = eng.createView().also {
                it.camera = camera
                it.scene = scene
                it.blendMode = FilamentView.BlendMode.TRANSLUCENT
                it.antiAliasing = if (config.antiAliasing) FilamentView.AntiAliasing.FXAA else FilamentView.AntiAliasing.NONE
            }
            val provider = UbershaderProvider(eng)
            materialProvider = provider
            assetLoader = AssetLoader(eng, provider, EntityManager.get())
            resourceLoader = ResourceLoader(eng)
            setupLights(eng)
            updateViewport()
            rebuildSwapChain()
            null
        } catch (t: Throwable) {
            releaseEngine()
            "The 3D engine could not start on this phone (${t.javaClass.simpleName})."
        }
    }

    private fun setupLights(eng: Engine) {
        // Key light (cool white, from the front), cyan fill from the side, blue rim from behind.
        addDirectional(eng, 0.85f, 0.95f, 1.0f, 70_000f, -0.3f, -0.5f, -1.0f)
        addDirectional(eng, 0.30f, 0.80f, 1.0f, 25_000f, 1.0f, -0.2f, -0.4f)
        addDirectional(eng, 0.40f, 0.55f, 1.0f, 30_000f, 0.0f, -0.3f, 1.0f)
        indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.55f, 0.62f, 0.8f))
            .intensity(30_000f)
            .build(eng)
        scene?.indirectLight = indirectLight
    }

    private fun addDirectional(
        eng: Engine, r: Float, g: Float, b: Float, lux: Float, dx: Float, dy: Float, dz: Float
    ) {
        val e = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(lux)
            .direction(dx, dy, dz)
            .castShadows(false)
            .build(eng, e)
        scene?.addEntity(e)
        lightEntities.add(e)
    }

    // ------------------------------------------------------------------ model

    /**
     * Put a GLB on screen. Throws with a readable message if the engine rejects it.
     * Run on the main thread. The ByteBuffer must be a direct buffer.
     */
    fun loadModel(data: ByteBuffer, glb: GlbInfo, source: String, sizeBytes: Long): ModelInfo {
        val eng = engine ?: error("3D engine is not running.")
        val loader = assetLoader ?: error("3D engine is not running.")
        unloadModel()

        val a = loader.createAsset(data) ?: error("The 3D engine could not read this GLB (unsupported or damaged).")
        try {
            resourceLoader?.loadResources(a)
        } catch (t: Throwable) {
            loader.destroyAsset(a)
            throw t
        }
        a.releaseSourceData()
        scene?.addEntities(a.entities)
        asset = a
        animator = a.instance?.animator

        // skeletal clips
        val clips = ArrayList<ClipInfo>()
        animator?.let { an ->
            for (i in 0 until an.animationCount) {
                clips += ClipInfo(i, an.getAnimationName(i) ?: "animation_$i", an.getAnimationDuration(i))
            }
        }
        brain.animation.setClips(clips)

        // face morph targets
        val rm = eng.renderableManager
        val bindings = ArrayList<MorphBinding>()
        val matched = LinkedHashSet<FaceShape>()
        var maxTargets = 0
        for (e in a.entities) {
            if (!rm.hasComponent(e)) continue
            val names = runCatching { a.getMorphTargetNames(e) }.getOrNull() ?: continue
            if (names.isEmpty()) continue
            maxTargets = max(maxTargets, names.size)
            val map = BlendShapeResolver.resolve(names.toList())
            if (map.isEmpty()) continue
            matched.addAll(map.keys)
            bindings += MorphBinding(
                instance = rm.getInstance(e),
                shapes = map.keys.toTypedArray(),
                indices = map.values.toTypedArray(),
                weights = FloatArray(names.size)
            )
        }
        morphBindings = bindings

        // head bone and root, for procedural head motion
        val tm = eng.transformManager
        val headName = glb.nodeNames.firstOrNull { HEAD_NAME.containsMatchIn(it) }
        headEntity = headName?.let { a.getFirstEntityByName(it) } ?: 0
        if (headEntity != 0 && tm.hasComponent(headEntity)) {
            tm.getTransform(tm.getInstance(headEntity), headBase)
        } else {
            headEntity = 0
        }
        rootEntity = a.root
        if (rootEntity != 0 && tm.hasComponent(rootEntity)) {
            tm.getTransform(tm.getInstance(rootEntity), rootBase)
        } else {
            rootEntity = 0
        }

        frameModel(a, tm)
        updateViewport()

        val warnings = ArrayList<String>()
        glb.riskyExtensions.forEach { warnings += "uses $it, which may not load; export without compression" }
        if (bindings.isEmpty() && maxTargets == 0) warnings += "no morph targets, so no facial expressions or lip movement"
        if (bindings.isEmpty() && maxTargets > 0) warnings += "morph targets exist but none match known face shape names"

        modelLoaded = true
        consecutiveErrors = 0
        if (visible) start()
        return ModelInfo(
            source = source,
            sizeBytes = sizeBytes,
            animationNames = clips.map { it.name },
            morphTargetCount = maxTargets,
            matchedFaceShapes = matched,
            gesturesAvailable = brain.animation.available,
            headBoneFound = headEntity != 0,
            hasSkin = glb.skinCount > 0,
            warnings = warnings
        )
    }

    private fun frameModel(a: FilamentAsset, tm: com.google.android.filament.TransformManager) {
        val box = a.boundingBox
        val c = box.center
        val h = box.halfExtent
        pivot[0] = c[0]; pivot[1] = c[1]; pivot[2] = c[2]
        val height = max(h[1] * 2f, 0.001f)
        val width = max(h[0] * 2f, 0.001f)
        val minY = c[1] - h[1]
        var headY = Float.NaN
        if (headEntity != 0) {
            val world = FloatArray(16)
            tm.getWorldTransform(tm.getInstance(headEntity), world)
            headY = world[13]
        }
        val fullBody = if (!headY.isNaN()) (headY - minY) / height > 0.65f else height > width * 1.6f
        targetX = c[0]
        targetZ = c[2]
        if (fullBody) {
            // upper body: head and shoulders fill the view
            frameHeight = height * 0.40f
            targetY = if (!headY.isNaN()) headY - frameHeight * 0.18f else (c[1] + h[1]) - frameHeight * 0.52f
        } else {
            frameHeight = height * 1.15f
            targetY = c[1]
        }
    }

    fun unloadModel() {
        val eng = engine
        val a = asset
        modelLoaded = false
        stop()
        if (eng != null && a != null) {
            scene?.removeEntities(a.entities)
            assetLoader?.destroyAsset(a)
        }
        asset = null
        animator = null
        morphBindings = emptyList()
        headEntity = 0
        rootEntity = 0
        brain.animation.setClips(emptyList())
    }

    // ------------------------------------------------------------------ frame loop

    /** Rendering runs only while the view is visible AND a model is loaded. */
    fun setVisible(v: Boolean) {
        visible = v
        if (v) start() else stop()
    }

    private fun start() {
        if (running || destroyed || engine == null || !modelLoaded || !visible) return
        running = true
        lastRenderNanos = 0L
        brain.resetClock()
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)
            val minInterval = 1_000_000_000L / config.targetFps
            if (lastRenderNanos != 0L && frameTimeNanos - lastRenderNanos < minInterval - 2_000_000L) return
            lastRenderNanos = frameTimeNanos
            try {
                brain.tick()
                applyPose()
                drawFrame(frameTimeNanos)
                consecutiveErrors = 0
            } catch (t: Throwable) {
                consecutiveErrors++
                if (consecutiveErrors >= 3) {
                    stop()
                    onFatalError?.invoke("Drawing the 3D avatar failed repeatedly (${t.javaClass.simpleName}).")
                }
            }
        }
    }

    private fun applyPose() {
        val eng = engine ?: return
        val tm = eng.transformManager
        var skinDirty = false

        val clip = brain.currentClip
        val an = animator
        if (an != null && clip != null) {
            an.applyAnimation(clip.index, clip.timeSec)
            skinDirty = true
        }

        // face
        val rm = eng.renderableManager
        val w = brain.expression.weights
        for (b in morphBindings) {
            java.util.Arrays.fill(b.weights, 0f)
            for (s in b.shapes.indices) {
                val value = w[b.shapes[s].ordinal]
                if (value <= 0.001f) continue
                for (idx in b.indices[s]) if (value > b.weights[idx]) b.weights[idx] = value
            }
            rm.setMorphWeights(b.instance, b.weights, 0)
        }

        // head and body sway (skipped while a clip is driving the skeleton, so the two do not fight)
        if (config.proceduralMotion && clip == null) {
            val e = brain.expression
            if (headEntity != 0) {
                Matrix.setIdentityM(scratchA, 0)
                Matrix.rotateM(scratchA, 0, e.headPitch, 1f, 0f, 0f)
                Matrix.rotateM(scratchA, 0, e.headYaw, 0f, 1f, 0f)
                Matrix.rotateM(scratchA, 0, e.headRoll, 0f, 0f, 1f)
                Matrix.multiplyMM(scratchOut, 0, headBase, 0, scratchA, 0)
                tm.setTransform(tm.getInstance(headEntity), scratchOut)
                skinDirty = true
            } else if (rootEntity != 0) {
                Matrix.setIdentityM(scratchA, 0)
                Matrix.translateM(scratchA, 0, pivot[0], pivot[1], pivot[2])
                Matrix.rotateM(scratchA, 0, e.headYaw * 0.6f, 0f, 1f, 0f)
                Matrix.rotateM(scratchA, 0, e.headRoll * 0.5f, 0f, 0f, 1f)
                Matrix.translateM(scratchA, 0, -pivot[0], -pivot[1], -pivot[2])
                Matrix.multiplyMM(scratchB, 0, rootBase, 0, scratchA, 0)
                tm.setTransform(tm.getInstance(rootEntity), scratchB)
            }
        }
        if (skinDirty) an?.updateBoneMatrices()
    }

    private fun drawFrame(frameTimeNanos: Long) {
        val sc = swapChain ?: return
        val helper = uiHelper ?: return
        if (!helper.isReadyToRender) return
        val r = renderer ?: return
        val v = view ?: return
        if (r.beginFrame(sc, frameTimeNanos)) {
            r.render(v)
            r.endFrame()
        }
    }

    // ------------------------------------------------------------------ camera

    private fun updateViewport() {
        val v = view ?: return
        val cam = camera ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return
        v.viewport = Viewport(0, 0, viewWidth, viewHeight)
        val fov = 28.0
        val distance = (frameHeight / 2.0) / tan(Math.toRadians(fov / 2.0))
        val aspect = viewWidth.toDouble() / viewHeight.toDouble()
        cam.setProjection(fov, aspect, distance * 0.05, distance * 20.0, Camera.Fov.VERTICAL)
        cam.lookAt(
            targetX.toDouble(), targetY.toDouble(), targetZ + distance,
            targetX.toDouble(), targetY.toDouble(), targetZ.toDouble(),
            0.0, 1.0, 0.0
        )
    }

    // ------------------------------------------------------------------ cleanup

    /** Release every GPU object. Safe to call more than once. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        stop()
        unloadModel()
        detachSurface()
        releaseEngine()
    }

    private fun releaseEngine() {
        val eng = engine ?: return
        runCatching {
            for (e in lightEntities) {
                scene?.removeEntity(e)
                eng.lightManager.destroy(e)
                eng.destroyEntity(e)
                EntityManager.get().destroy(e)
            }
            lightEntities.clear()
            indirectLight?.let { eng.destroyIndirectLight(it) }
            indirectLight = null
            assetLoader?.destroy()
            resourceLoader?.destroy()
            materialProvider?.destroyMaterials()
            materialProvider?.destroy()
            swapChain?.let { eng.destroySwapChain(it) }
            view?.let { eng.destroyView(it) }
            renderer?.let { eng.destroyRenderer(it) }
            scene?.let { eng.destroyScene(it) }
            if (cameraEntity != 0) {
                eng.destroyCameraComponent(cameraEntity)
                EntityManager.get().destroy(cameraEntity)
            }
            eng.flushAndWait()
            eng.destroy()
        }
        engine = null
        renderer = null
        scene = null
        view = null
        camera = null
        swapChain = null
        assetLoader = null
        resourceLoader = null
        materialProvider = null
        cameraEntity = 0
    }

    private companion object {
        /** Matches bone names like Head, mixamorig:Head, J_Bip_C_Head, head_01. */
        val HEAD_NAME = Regex("""(^|[:_.\s])head($|[_.\s]?\d*$)""", RegexOption.IGNORE_CASE)
    }
}
