package com.feerlessleadr.embidextrous.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.EmbyUser
import com.feerlessleadr.embidextrous.data.ImageInfo
import com.feerlessleadr.embidextrous.data.ListDefaults
import com.feerlessleadr.embidextrous.data.ListDraft
import com.feerlessleadr.embidextrous.data.Preview
import com.feerlessleadr.embidextrous.data.Safety
import com.feerlessleadr.embidextrous.data.Swatch
import com.feerlessleadr.embidextrous.data.Templates
import com.feerlessleadr.embidextrous.data.ValueOption
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class EditorUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val catalog: Catalog? = null,
    val users: List<EmbyUser> = emptyList(),
    /** The plugin's defaults for new lists (empty when they could not be read). */
    val defaults: ListDefaults = ListDefaults(),
    val libraries: List<ValueOption> = emptyList(),
    /** What a "similar to" rule can compare. */
    val similarityFields: List<ValueOption> = emptyList(),
    val draft: ListDraft? = null,
    /** A new list starts by choosing a template. */
    val choosingTemplate: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    /** Custom images of an existing list, and small previews of them by image type. */
    val images: List<ImageInfo> = emptyList(),
    val previews: Map<String, Bitmap> = emptyMap(),
    val imageBusy: Boolean = false,
    val imageMessage: String? = null,
    /** The draft as it was when editing began, to tell whether anything changed. */
    val baseline: JsonObject? = null,
    /** The colour this list is marked with in the overview (kept on this phone), and what it was at the start. */
    val listColor: Swatch? = null,
    val baselineColor: Swatch? = null,
    /** A preview of the list's contents: null while no preview is open. */
    val preview: PreviewUiState? = null,
) {
    /** True when the form differs from what was loaded (a freshly chosen template that was not touched is not a change). */
    val dirty: Boolean get() = draft != null && baseline != null && (draft.toJson() != baseline || listColor != baselineColor)
}

data class PreviewUiState(val loading: Boolean = true, val result: Preview? = null, val error: String? = null)

/** Creates a new list (when [listId] is null) or edits an existing one. */
class EditorViewModel(private val container: AppContainer, private val listId: String?, private val copyOf: String? = null) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState(choosingTemplate = listId == null && copyOf == null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            val api = container.api() ?: return@launch
            try {
                coroutineScope {
                    val catalog = async { api.catalog() }
                    val users = async { runCatching { api.users() }.getOrDefault(emptyList()) }
                    val libraries = async { runCatching { api.libraries() }.getOrDefault(emptyList()) }
                    val similarity = async { runCatching { api.similarityFields() }.getOrDefault(emptyList()) }
                    val defaults = async { runCatching { api.listDefaults() }.getOrDefault(ListDefaults()) }
                    val doc = (listId ?: copyOf)?.let { id -> async { api.list(id) } }
                    val loadedUsers = users.await()
                    val loadedDraft = doc?.await()?.let { d -> ListDraft.from(d).let { draft -> if (copyOf != null) draft.asCopy() else draft } }
                    val color = listId?.let { id -> container.settings.listColors.value[id] }
                    _state.update {
                        val draft = loadedDraft ?: it.draft
                        it.copy(
                            loading = false,
                            catalog = catalog.await(),
                            users = loadedUsers,
                            defaults = defaults.await(),
                            libraries = libraries.await(),
                            similarityFields = similarity.await(),
                            draft = draft,
                            baseline = if (loadedDraft != null) loadedDraft.toJson() else it.baseline,
                            listColor = if (loadedDraft != null) color else it.listColor,
                            baselineColor = if (loadedDraft != null) color else it.baselineColor,
                        )
                    }
                }
                if (listId != null) loadImages()
            } catch (e: ApiException) {
                if (e.isUnauthorized) container.signOut() else _state.update { it.copy(loading = false, loadError = e.message) }
            }
        }
    }

    fun chooseTemplate(id: String) {
        val users = _state.value.users
        val me = container.session.value?.userId
        val owner = users.firstOrNull { it.id.equals(me, ignoreCase = true) }?.id ?: users.firstOrNull()?.id
        var draft = Templates.draft(id, owner)
        // The plugin's own defaults for new lists: all of them for a blank list, and the ones a template leaves open for a template.
        val defaults = _state.value.defaults
        draft = if (id == "blank") defaults.applyToBlank(draft, _state.value.catalog?.sorts?.map { it.value }?.toSet()) else defaults.applyToTemplate(draft)
        _state.update { it.copy(draft = draft, baseline = draft.toJson(), choosingTemplate = false) }
    }

    /** Starts a new list from shared JSON. Returns a sentence to show when the text is not a list, or null on success. */
    fun importShared(text: String): String? {
        val users = _state.value.users
        val me = container.session.value?.userId
        val owner = users.firstOrNull { it.id.equals(me, ignoreCase = true) }?.id ?: users.firstOrNull()?.id
        val draft = ListDraft.fromShared(text, owner) ?: return "That is not a shared SmartLists list."
        _state.update { it.copy(draft = draft, baseline = draft.toJson(), choosingTemplate = false) }
        return null
    }

    fun setListColor(swatch: Swatch?) {
        _state.update { it.copy(listColor = swatch) }
    }

    /** Asks the server which items the list as edited would contain (needs plugin 0.1.10 or newer). */
    fun preview() {
        val s = _state.value
        val draft = s.draft ?: return
        val named = if (draft.name.isBlank()) draft.copy(name = "Preview") else draft
        named.validate(s.catalog)?.let { problem ->
            _state.update { it.copy(preview = PreviewUiState(loading = false, error = problem)) }
            return
        }
        _state.update { it.copy(preview = PreviewUiState(loading = true)) }
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                val result = api.preview(named.toJson())
                _state.update { it.copy(preview = PreviewUiState(loading = false, result = result)) }
            } catch (e: ApiException) {
                if (e.isUnauthorized) {
                    container.signOut()
                } else {
                    val text = if (e.status == 404) "Preview needs SmartLists plugin 0.1.10 or newer on your server." else e.message.orEmpty()
                    _state.update { it.copy(preview = PreviewUiState(loading = false, error = text)) }
                }
            }
        }
    }

    fun closePreview() {
        _state.update { it.copy(preview = null) }
    }

    fun update(change: (ListDraft) -> ListDraft) {
        _state.update { s -> s.copy(draft = s.draft?.let(change), saveError = null) }
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val draft = s.draft ?: return
        draft.validate(s.catalog)?.let { problem ->
            _state.update { it.copy(saveError = problem) }
            return
        }
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(saving = true, saveError = null) }
            try {
                val json = draft.toJson()
                val saved = if (draft.isNew) api.create(json) else api.update(listId.orEmpty(), json)
                // The colour mark lives on this phone, keyed by the list's id.
                saved.id.ifBlank { listId.orEmpty() }.takeIf { it.isNotBlank() }?.let { container.settings.setListColor(it, s.listColor) }
                container.notifyListsChanged()
                _state.update { it.copy(saving = false) }
                onSaved()
            } catch (e: ApiException) {
                if (e.isUnauthorized) {
                    container.signOut()
                } else {
                    _state.update { it.copy(saving = false, saveError = e.message) }
                }
            }
        }
    }

    // ----- custom images (existing lists only: they are stored per list id) ------------------------------------

    private fun loadImages() {
        val id = listId ?: return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                val images = api.images(id)
                _state.update { it.copy(images = images) }
                images.forEach { image ->
                    launch {
                        val bitmap = runCatching { Safety.decodeBounded(api.imageBytes(id, image.type), 600) }.getOrNull()
                        if (bitmap != null) _state.update { it.copy(previews = it.previews + (image.type to bitmap)) }
                    }
                }
            } catch (e: ApiException) {
                if (e.isUnauthorized) container.signOut() else _state.update { it.copy(imageMessage = e.message) }
            }
        }
    }

    fun uploadImage(imageType: String, fileName: String, mimeType: String, bytes: ByteArray) {
        val id = listId ?: return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(imageBusy = true, imageMessage = null) }
            try {
                api.uploadImage(id, imageType, fileName, mimeType, bytes)
                _state.update { it.copy(previews = it.previews - imageType) }
                loadImages()
                container.notifyListsChanged()
            } catch (e: ApiException) {
                if (e.isUnauthorized) container.signOut() else _state.update { it.copy(imageMessage = e.message) }
            } finally {
                _state.update { it.copy(imageBusy = false) }
            }
        }
    }

    fun deleteImage(imageType: String) {
        val id = listId ?: return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(imageBusy = true, imageMessage = null) }
            try {
                api.deleteImage(id, imageType)
                _state.update { s -> s.copy(images = s.images.filterNot { it.type == imageType }, previews = s.previews - imageType) }
                container.notifyListsChanged()
            } catch (e: ApiException) {
                if (e.isUnauthorized) container.signOut() else _state.update { it.copy(imageMessage = e.message) }
            } finally {
                _state.update { it.copy(imageBusy = false) }
            }
        }
    }

    fun imageProblem(message: String) {
        _state.update { it.copy(imageMessage = message) }
    }
}
