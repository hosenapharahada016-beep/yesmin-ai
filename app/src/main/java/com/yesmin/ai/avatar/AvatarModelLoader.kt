package com.yesmin.ai.avatar

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What a quick read of a GLB file's JSON header tells us, before any 3D engine is involved. */
data class GlbInfo(
    val animationNames: List<String>,
    val morphTargetNames: List<String>,
    val nodeNames: List<String>,
    val meshCount: Int,
    val skinCount: Int,
    val imageCount: Int,
    val usesExternalFiles: Boolean,
    val riskyExtensions: List<String>
)

/** Reads the JSON chunk of a binary glTF 2.0 (.glb) file. Throws IllegalArgumentException with a plain message. */
object GlbParser {
    private const val MAGIC = 0x46546C67      // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A // "JSON"
    private val RISKY = listOf("KHR_draco_mesh_compression", "EXT_meshopt_compression", "KHR_texture_basisu")

    fun hasGlbHeader(first4: ByteArray): Boolean =
        first4.size >= 4 && first4[0] == 'g'.code.toByte() && first4[1] == 'l'.code.toByte() &&
            first4[2] == 'T'.code.toByte() && first4[3] == 'F'.code.toByte()

    fun parse(buffer: ByteBuffer): GlbInfo {
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        b.position(0)
        require(b.remaining() >= 20) { "File is too small to be a GLB." }
        require(b.int == MAGIC) { "Not a binary glTF file. Export your model as .glb (glTF 2.0 binary)." }
        val version = b.int
        require(version == 2) { "Unsupported glTF version $version. Use glTF 2.0." }
        val total = b.int
        require(total in 20..buffer.limit()) { "The GLB file is truncated or damaged." }
        val jsonLength = b.int
        require(b.int == CHUNK_JSON) { "The GLB has no JSON section." }
        require(jsonLength in 2..b.remaining()) { "The GLB JSON section is damaged." }
        val bytes = ByteArray(jsonLength)
        b.get(bytes)
        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8).trimEnd(' ', '\u0000', '\n', '\r'))
        } catch (e: Exception) {
            throw IllegalArgumentException("The GLB JSON could not be read.")
        }

        val animations = json.optJSONArray("animations")
        val animationNames = (0 until (animations?.length() ?: 0)).map { i ->
            animations!!.getJSONObject(i).optString("name").ifBlank { "animation_$i" }
        }

        val morphNames = LinkedHashSet<String>()
        val meshes = json.optJSONArray("meshes")
        for (m in 0 until (meshes?.length() ?: 0)) {
            val mesh = meshes!!.getJSONObject(m)
            collectTargetNames(mesh.optJSONObject("extras"), morphNames)
            val prims = mesh.optJSONArray("primitives")
            for (p in 0 until (prims?.length() ?: 0)) {
                collectTargetNames(prims!!.getJSONObject(p).optJSONObject("extras"), morphNames)
            }
        }

        val nodes = json.optJSONArray("nodes")
        val nodeNames = (0 until (nodes?.length() ?: 0)).mapNotNull { i ->
            nodes!!.getJSONObject(i).optString("name").takeIf { it.isNotBlank() }
        }

        var external = false
        val buffers = json.optJSONArray("buffers")
        for (i in 0 until (buffers?.length() ?: 0)) {
            val uri = buffers!!.getJSONObject(i).optString("uri")
            if (uri.isNotEmpty() && !uri.startsWith("data:")) external = true
        }
        val images = json.optJSONArray("images")
        for (i in 0 until (images?.length() ?: 0)) {
            val uri = images!!.getJSONObject(i).optString("uri")
            if (uri.isNotEmpty() && !uri.startsWith("data:")) external = true
        }

        val used = json.optJSONArray("extensionsUsed")
        val risky = (0 until (used?.length() ?: 0)).map { used!!.getString(it) }.filter { it in RISKY }

        return GlbInfo(
            animationNames, morphNames.toList(), nodeNames,
            meshes?.length() ?: 0, json.optJSONArray("skins")?.length() ?: 0, images?.length() ?: 0,
            external, risky
        )
    }

    private fun collectTargetNames(extras: JSONObject?, out: MutableSet<String>) {
        val names = extras?.optJSONArray("targetNames") ?: return
        for (i in 0 until names.length()) out.add(names.optString(i))
    }
}

/**
 * Finds, reads and checks the avatar model. Order of preference:
 *  1. a model imported in Settings (private app storage)
 *  2. app/src/main/assets/avatar/avatar.glb (bundled in the APK)
 *  3. nothing: the caller shows a placeholder
 * No 3D engine is touched here, so this works (and can be tested) without Filament.
 */
class AvatarModelLoader(private val context: Context, private val config: AvatarConfiguration) {

    sealed class LoadResult {
        class Success(val buffer: ByteBuffer, val glb: GlbInfo, val source: String, val bytes: Long) : LoadResult()
        class Missing(val reason: String) : LoadResult()
        class Invalid(val message: String) : LoadResult()
    }

    private val customFile get() = File(context.filesDir, AvatarConfiguration.CUSTOM_MODEL_PATH)

    fun hasCustomModel(): Boolean = customFile.isFile

    suspend fun load(): LoadResult = withContext(Dispatchers.IO) {
        try {
            val custom = customFile
            if (custom.isFile) {
                return@withContext readFile(custom, "imported model")
            }
            val bundled = runCatching {
                context.assets.list(AvatarConfiguration.ASSET_DIR)?.contains(AvatarConfiguration.MODEL_FILE) == true
            }.getOrDefault(false)
            if (bundled) return@withContext readAsset()
            LoadResult.Missing(
                "No 3D model installed. Put a GLB at app/src/main/assets/avatar/avatar.glb or import one in Settings."
            )
        } catch (oom: OutOfMemoryError) {
            LoadResult.Invalid("Not enough memory to load this model. Use a smaller one.")
        } catch (e: Exception) {
            LoadResult.Invalid(e.message ?: "The model could not be read.")
        }
    }

    private fun readFile(f: File, label: String): LoadResult {
        val size = f.length()
        tooBig(size)?.let { return LoadResult.Invalid(it) }
        FileInputStream(f).use { return finish(it, size, label) }
    }

    private fun readAsset(): LoadResult {
        val size = runCatching { context.assets.openFd(AvatarConfiguration.ASSET_MODEL_PATH).use { it.length } }
            .getOrDefault(-1L)
        if (size >= 0) tooBig(size)?.let { return LoadResult.Invalid(it) }
        context.assets.open(AvatarConfiguration.ASSET_MODEL_PATH).use {
            return finish(it, size, "bundled model (assets/avatar/avatar.glb)")
        }
    }

    private fun tooBig(size: Long): String? =
        if (size > config.maxModelBytes)
            "Model is ${size / (1024 * 1024)} MB. The limit for this phone is ${config.maxModelBytes / (1024 * 1024)} MB. " +
                "Reduce texture sizes or polygon count."
        else null

    private fun finish(input: InputStream, knownSize: Long, label: String): LoadResult {
        val data = readToDirectBuffer(input, knownSize)
        val glb = try {
            GlbParser.parse(data)
        } catch (e: IllegalArgumentException) {
            return LoadResult.Invalid(e.message ?: "Invalid GLB.")
        }
        if (glb.usesExternalFiles) {
            return LoadResult.Invalid("The model refers to separate texture or data files. Export a self-contained .glb.")
        }
        return LoadResult.Success(data, glb, label, data.limit().toLong())
    }

    private fun readToDirectBuffer(input: InputStream, knownSize: Long): ByteBuffer {
        val cap = config.maxModelBytes
        val tmp = ByteArray(64 * 1024)
        if (knownSize in 1..cap) {
            val bb = ByteBuffer.allocateDirect(knownSize.toInt())
            while (bb.hasRemaining()) {
                val n = input.read(tmp, 0, minOf(tmp.size, bb.remaining()))
                if (n < 0) break
                bb.put(tmp, 0, n)
            }
            bb.flip()
            return bb
        }
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val n = input.read(tmp)
            if (n < 0) break
            out.write(tmp, 0, n)
            require(out.size() <= cap) { tooBig(out.size().toLong()) ?: "Model too large." }
        }
        val bytes = out.toByteArray()
        return ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.flip() }
    }

    /** Copy a user-picked .glb into private storage after checking it. @return size in bytes */
    suspend fun importFrom(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = customFile.parentFile!!.apply { mkdirs() }
            val tmp = File(dir, "import.tmp")
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open the file." }
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            require(total <= config.maxModelBytes) { tooBig(total) ?: "Model too large." }
                            out.write(buf, 0, n)
                        }
                    }
                }
                val check = readFile(tmp, "imported model")
                if (check is LoadResult.Invalid) error(check.message)
                if (customFile.exists()) customFile.delete()
                require(tmp.renameTo(customFile)) { "Could not save the model." }
                customFile.length()
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    fun removeCustomModel(): Boolean = !customFile.exists() || customFile.delete()
}
