package Verdant.Vista.ui.transform

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import Verdant.Vista.R
import Verdant.Vista.VerdantVistaApplication
import Verdant.Vista.data.db.FavoriteEntity
import Verdant.Vista.data.model.Taxon
import Verdant.Vista.data.repository.ObservationRepository
import Verdant.Vista.databinding.FragmentTransformBinding
import Verdant.Vista.ui.SharedTaxonViewModel
import Verdant.Vista.ui.widget.MemoryWidget
import Verdant.Vista.util.AppConstants
import Verdant.Vista.util.NetworkUtils
import Verdant.Vista.util.PhotoUtils
import Verdant.Vista.util.WikiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ObservationRepository
    private lateinit var viewModel: TransformViewModel
    private lateinit var sharedViewModel: SharedTaxonViewModel
    
    private var favoriteJob: Job? = null
    private var detailsJob: Job? = null
    private var soundsJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentProgressIndicator: CircularProgressIndicator? = null
    
    private val snapHelper = PagerSnapHelper()

    companion object {
        private const val TAG = "TransformFragment"
        private const val ARG_TAXON_ID = "taxon_id"
        
        private val IGNORED_SECTIONS = setOf(
            "See also", "References", "Further reading", "External links", 
            "Notes", "Citations", "Bibliography"
        )
    }

    private var isFavorite = false
    private var currentPhotos = emptyList<String>()
    private var currentSummary: String? = null
    private var currentTaxonomy: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransformBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDependencies()
        setupUI()
        setupObservers()
    }

    private fun setupDependencies() {
        val app = requireActivity().application as VerdantVistaApplication
        repository = app.repository

        viewModel = ViewModelProvider(this).get(TransformViewModel::class.java)
        sharedViewModel = ViewModelProvider(requireActivity()).get(SharedTaxonViewModel::class.java)
    }

    private fun setupUI() {
        val layoutManager = object : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {
            override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
                val extraSpace = width * 2
                extraLayoutSpace[0] = extraSpace
                extraLayoutSpace[1] = extraSpace
            }
        }
        
        binding.recyclerImages.layoutManager = layoutManager
        binding.recyclerImages.setItemViewCacheSize(5)
        binding.recyclerImages.setHasFixedSize(false)
        
        // Attach SnapHelper only once to prevent crashes
        snapHelper.attachToRecyclerView(binding.recyclerImages)

        binding.btnSettingsTop.setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }

        binding.btnSettingsBottom.setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }
    }

    private fun setupObservers() {
        sharedViewModel.requestedTaxonId.observe(viewLifecycleOwner) { taxonId ->
            if (taxonId != null) {
                stopPlayback()
                viewModel.fetchTaxonById(taxonId)
                sharedViewModel.clearRequestedTaxonId()
            }
        }

        sharedViewModel.refreshRequested.observe(viewLifecycleOwner) { requested ->
            if (requested == true) {
                stopPlayback()
                
                viewModel.fetchRandomPlant()
                sharedViewModel.clearRefreshRequest()
            }
        }

        val argId = arguments?.getInt(ARG_TAXON_ID, -1) ?: -1
        if (argId != -1) {
            viewModel.fetchTaxonById(argId)
            arguments?.remove(ARG_TAXON_ID)
        }

        viewModel.discoveredTaxon.observe(viewLifecycleOwner) { taxon ->
            if (taxon != null) {
                displayTaxon(taxon)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressLoader.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        if (viewModel.discoveredTaxon.value == null && sharedViewModel.requestedTaxonId.value == null && argId == -1) {
            viewModel.fetchRandomPlant()
        }
    }

    private fun displayTaxon(taxon: Taxon) {
        // We no longer clear names/details immediately.
        // Instead, we only update them when we have new content.
        // This keeps the screen populated during the "Scouting" phase.
        
        // Update names instantly
        binding.textCommonName.text = taxon.getDisplayName()
        binding.textScientificName.text = taxon.name ?: ""

        Log.d(TAG, "displayTaxon: ${taxon.getDisplayName()}, Peak=${taxon.isPeakSeason}, Native=${taxon.establishmentMeans}")

        // --- Seasonal Badge Logic ---
        when {
            taxon.isRare -> {
                binding.chipSeasonalBadge.visibility = View.VISIBLE
                binding.chipSeasonalBadge.text = getString(R.string.badge_rare)
                binding.chipSeasonalBadge.setChipIconResource(R.drawable.ic_star_black_24dp)
            }
            taxon.isSeasonalFirst -> {
                binding.chipSeasonalBadge.visibility = View.VISIBLE
                binding.chipSeasonalBadge.text = getString(R.string.badge_seasonal_first)
                binding.chipSeasonalBadge.setChipIconResource(R.drawable.ic_star_black_24dp)
            }
            taxon.isPeakSeason -> {
                binding.chipSeasonalBadge.visibility = View.VISIBLE
                binding.chipSeasonalBadge.text = getString(R.string.badge_peak_season)
                binding.chipSeasonalBadge.setChipIconResource(R.drawable.ic_star_black_24dp)
            }
            taxon.isLocalFavorite -> {
                binding.chipSeasonalBadge.visibility = View.VISIBLE
                binding.chipSeasonalBadge.text = getString(R.string.badge_local_favorite)
                binding.chipSeasonalBadge.setChipIconResource(R.drawable.ic_star_black_24dp)
            }
            else -> {
                binding.chipSeasonalBadge.visibility = View.GONE
            }
        }

        // --- Native Badge Logic ---
        if (taxon.isNative()) {
            binding.chipNativeBadge.visibility = View.VISIBLE
        } else {
            binding.chipNativeBadge.visibility = View.GONE
        }

        // Clear sound controls immediately for the new taxon
        binding.soundControlsContainer.removeAllViews()
        binding.soundControlsContainer.visibility = View.GONE

        // FORCE IMAGE AREA VISIBILITY
        // We use the initial photo immediately to ensure the area isn't 0dp
        val initialPhoto = taxon.defaultPhoto?.mediumUrl ?: taxon.defaultPhoto?.url
        val initialPhotos = if (initialPhoto != null) listOf(initialPhoto) else emptyList()
        
        if (initialPhotos.isNotEmpty()) {
            val currentAdapter = binding.recyclerImages.adapter as? ImageAdapter
            if (currentAdapter != null) {
                currentAdapter.updateImages(initialPhotos)
            } else {
                val adapter = ImageAdapter(initialPhotos)
                binding.recyclerImages.adapter = adapter
            }
            currentPhotos = initialPhotos
        }

        // Details (Wiki, Taxonomy, Sounds) will stay as they were from the 
        // PREVIOUS species until the new "detailsJob" finishes.
        // This eliminates the "Layout Jump" and the "Blank Content" feeling.

        favoriteJob?.cancel()
        favoriteJob = viewLifecycleOwner.lifecycleScope.launch {
            repository.isFavoriteFlow(taxon.id.toLong()).collectLatest { fav ->
                isFavorite = fav
                updateFavoriteButton(fav)
            }
        }

        currentSummary = null
        currentTaxonomy = buildTaxonomyText(taxon)
        // Only set taxonomy if we actually have data (prevents showing "Kingdom" immediately)
        if (taxon.ancestors.isNullOrEmpty()) {
            binding.textTaxonomy.text = ""
        } else {
            binding.textTaxonomy.text = currentTaxonomy
        }

        binding.btnAddFavorite.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val entity = FavoriteEntity(
                    id = taxon.id.toLong(),
                    scientificName = taxon.name ?: "Unknown",
                    commonName = taxon.commonName ?: taxon.name ?: "Unknown",
                    imageUrl = currentPhotos.firstOrNull() ?: taxon.defaultPhoto?.mediumUrl ?: "",
                    taxonType = when {
                        taxon.isBird() -> AppConstants.TAXON_BIRDS
                        taxon.isMammal() -> AppConstants.TAXON_MAMMALS
                        taxon.isInsect() -> AppConstants.TAXON_INSECTS
                        taxon.isFungi() -> AppConstants.TAXON_FUNGI
                        taxon.isHerp() -> AppConstants.TAXON_HERPS
                        taxon.isAquatic() -> AppConstants.TAXON_AQUATIC
                        else -> AppConstants.TAXON_PLANTS
                    },
                    summary = currentSummary ?: "",
                    taxonomy = currentTaxonomy
                )
                repository.toggleFavorite(entity, requireActivity().application)
            }
        }

        binding.btnShare.setOnClickListener {
            val shareText = "I just discovered ${taxon.commonName ?: taxon.name} (${taxon.name}) on Verdant Vista! \n\nCheck out this species: https://www.inaturalist.org/taxa/${taxon.id}"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        detailsJob?.cancel()
        detailsJob = viewLifecycleOwner.lifecycleScope.launch {
            val photosDeferred = async { repository.getResearchGradePhotos(taxon.id) }
            val wikiDeferred = async { repository.fetchWikiSummary(taxon) }
            
            val (photos, attributions) = photosDeferred.await()
            if (_binding != null && photos.isNotEmpty()) {
                currentPhotos = photos
                val adapter = binding.recyclerImages.adapter as? ImageAdapter
                if (adapter != null) {
                    adapter.updateImages(photos, attributions)
                } else {
                    binding.recyclerImages.adapter = ImageAdapter(photos, attributions)
                }
                preloadPhotos(photos)
                pushUpdateToWidgetBackground(taxon, photos.first())
            } else if (_binding != null) {
                taxon.defaultPhoto?.mediumUrl?.let { pushUpdateToWidgetBackground(taxon, it) }
            }

            // NOW we clear and update the background details
            if (_binding != null) {
                binding.wikiSectionsContainer.removeAllViews()
                binding.textTaxonomy.text = buildTaxonomyText(taxon)
            }

            val summaryContent = wikiDeferred.await() ?: repository.getTaxonDetails(taxon.id)?.summary
            if (_binding != null) {
                // Update technical details now that background fetch is done
                if (summaryContent != null) {
                    val cleanedSummary = WikiUtils.cleanExtract(summaryContent)
                    currentSummary = cleanedSummary
                    parseAndDisplayWikiSections(cleanedSummary)
                }
            }

            // Re-check for sounds now that we have full details (and ancestors)
            val updatedTaxon = repository.getTaxonDetails(taxon.id)
            if (updatedTaxon != null && canHaveSounds(updatedTaxon)) {
                fetchAndDisplaySounds(updatedTaxon.id)
            }
        }

        if (canHaveSounds(taxon)) {
            fetchAndDisplaySounds(taxon.id)
        }
    }

    private fun canHaveSounds(taxon: Taxon): Boolean {
        val hasSounds = taxon.isBird() || taxon.isMammal() || taxon.isReptile() || taxon.isAmphibian() || taxon.isInsect()
        Log.d(TAG, "canHaveSounds for ${taxon.name} (ID: ${taxon.id}): $hasSounds (Bird: ${taxon.isBird()}, Mammal: ${taxon.isMammal()}, Insect: ${taxon.isInsect()})")
        return hasSounds
    }

    private fun fetchAndDisplaySounds(taxonId: Int) {
        Log.d(TAG, "fetchAndDisplaySounds for $taxonId")
        soundsJob?.cancel()
        soundsJob = viewLifecycleOwner.lifecycleScope.launch {
            val sounds = repository.getBirdSounds(taxonId)
            Log.d(TAG, "getBirdSounds for $taxonId returned ${sounds.size} sounds")
            if (_binding != null) {
                binding.soundControlsContainer.removeAllViews()
                if (sounds.isNotEmpty()) {
                    Log.d(TAG, "Displaying sound controls for $taxonId")
                    binding.soundControlsContainer.visibility = View.VISIBLE
                    sounds.forEach { pair ->
                        addSoundButton(pair.first, pair.second)
                    }
                } else {
                    Log.d(TAG, "No sounds found for $taxonId, hiding container")
                    binding.soundControlsContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun addSoundButton(url: String, durationMs: Int) {
        Log.d(TAG, "addSoundButton: adding button for $url")
        val soundView = layoutInflater.inflate(R.layout.item_sound_button, binding.soundControlsContainer, false)
        val playButton = soundView.findViewById<MaterialButton>(R.id.btn_play)
        val progressIndicator = soundView.findViewById<CircularProgressIndicator>(R.id.progress_circular)
        val durationText = soundView.findViewById<TextView>(R.id.text_duration)

        if (durationMs > 0) {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            durationText.text = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            durationText.visibility = View.VISIBLE
        }

        playButton.setOnClickListener {
            playBirdSound(url, progressIndicator)
        }

        binding.soundControlsContainer.addView(soundView)
    }

    private fun playBirdSound(url: String, progressIndicator: CircularProgressIndicator) {
        if (mediaPlayer?.isPlaying == true && currentProgressIndicator == progressIndicator) {
            stopPlayback()
            return
        }

        stopPlayback()
        currentProgressIndicator = progressIndicator
        currentProgressIndicator?.visibility = View.VISIBLE
        currentProgressIndicator?.progress = 0

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { 
                    start()
                    updateProgress()
                }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ -> 
                    stopPlayback()
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sound", e)
                stopPlayback()
            }
        }
    }

    private fun updateProgress() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                val total = player.duration
                if (total > 0) {
                    val current = player.currentPosition
                    currentProgressIndicator?.progress = (current * 100 / total)
                }
                handler.postDelayed({ updateProgress() }, 50)
            }
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)
        currentProgressIndicator?.visibility = View.INVISIBLE
        currentProgressIndicator = null
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun preloadPhotos(photoUrls: List<String>) {
        val context = context?.applicationContext ?: return
        photoUrls.forEach { url ->
            val finalUrl = PhotoUtils.getHighResUrl(url)
            val request = ImageRequest.Builder(context)
                .data(finalUrl)
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    private fun pushUpdateToWidgetBackground(taxon: Taxon, imageUrl: String) {
        val context = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = PhotoUtils.fetchBitmap(context, imageUrl)
                MemoryWidget.updateWidgetLive(
                    context,
                    taxon.id,
                    taxon.commonName ?: taxon.name,
                    taxon.name,
                    bitmap
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mirror update to widget", e)
            }
        }
    }


    private fun parseAndDisplayWikiSections(content: String) {
        val container = binding.wikiSectionsContainer
        container.removeAllViews()

        val sections = mutableListOf<Pair<String, String>>()
        val lines = content.lines()
        
        var currentTitle = "About"
        var currentContent = StringBuilder()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("==") && trimmedLine.endsWith("==")) {
                if (currentContent.isNotBlank() && !IGNORED_SECTIONS.contains(currentTitle)) {
                    sections.add(currentTitle to currentContent.toString().trim())
                }
                currentTitle = trimmedLine.replace("=", "").trim()
                currentContent = StringBuilder()
            } else {
                currentContent.append(line).append("\n")
            }
        }
        if (currentContent.isNotBlank() && !IGNORED_SECTIONS.contains(currentTitle)) {
            sections.add(currentTitle to currentContent.toString().trim())
        }

        sections.forEach { section ->
            val sectionView = layoutInflater.inflate(R.layout.item_wiki_section, container, false)
            val titleView = sectionView.findViewById<TextView>(R.id.text_section_title)
            val contentView = sectionView.findViewById<TextView>(R.id.text_section_content)
            val headerView = sectionView.findViewById<View>(R.id.section_header)
            val chevron = sectionView.findViewById<ImageView>(R.id.image_chevron)

            titleView.text = section.first
            contentView.text = section.second
            
            // All sections collapsed by default for a cleaner look
            val shouldExpand = false

            if (shouldExpand) {
                contentView.visibility = View.VISIBLE
                chevron.rotation = 180f
            }

            headerView.setOnClickListener {
                val isVisible = contentView.visibility == View.VISIBLE
                contentView.visibility = if (isVisible) View.GONE else View.VISIBLE
                chevron.animate().rotation(if (isVisible) 0f else 180f).setDuration(200).start()
            }

            container.addView(sectionView)
        }
    }

    private fun buildTaxonomyText(taxon: Taxon): String {
        val taxonomyText = StringBuilder()
        taxon.ancestors?.forEachIndexed { index, ancestor ->
            val indentation = "  ".repeat(index)
            val rank = ancestor.rank?.replaceFirstChar { it.uppercase() } ?: ""
            val displayName = ancestor.commonName?.let { "$it (${ancestor.name})" } ?: ancestor.name
            taxonomyText.append("$indentation↳ $rank: $displayName\n")
        }
        val indentation = "  ".repeat(taxon.ancestors?.size ?: 0)
        val currentRank = taxon.rank?.replaceFirstChar { it.uppercase() } ?: ""
        taxonomyText.append("$indentation↳ $currentRank: ${taxon.commonName ?: taxon.name} (${taxon.name})")
        return taxonomyText.toString()
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        if (_binding == null) return
        val green = android.graphics.Color.parseColor("#4CAF50")
        binding.btnAddFavorite.iconTint = android.content.res.ColorStateList.valueOf(
            if (isFavorite) green
            else (green and 0x00FFFFFF) or 0x40000000 // 25% alpha
        )
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPlayback()
        favoriteJob?.cancel()
        detailsJob?.cancel()
        soundsJob?.cancel()
        _binding = null
    }
}
