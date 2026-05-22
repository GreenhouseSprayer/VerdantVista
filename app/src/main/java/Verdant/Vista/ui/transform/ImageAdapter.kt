package Verdant.Vista.ui.transform

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Precision
import Verdant.Vista.R
import Verdant.Vista.databinding.ItemImageBinding
import Verdant.Vista.util.PhotoUtils

class ImageAdapter(
    private var images: List<String>,
    private var attributions: List<String?> = emptyList(),
    private val onImageRendered: () -> Unit = {}
) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    fun updateImages(newImages: List<String>, newAttributions: List<String?> = emptyList()) {
        this.images = newImages
        this.attributions = newAttributions
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = images[position]
        
        val originalUrl = PhotoUtils.getHighResUrl(url)
        val thumbUrl = PhotoUtils.getThumbUrl(url)

        // Reset zoom state and scale type for new images
        holder.binding.imageView.setScale(1.0f, false)
        holder.binding.imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        // Toggle between "Study View" and "Immersive View" on tap
        // Pinch-to-zoom is handled automatically by PhotoView
        holder.binding.imageView.setOnViewTapListener { _, _, _ ->
            val currentScaleType = holder.binding.imageView.scaleType
            if (currentScaleType == ImageView.ScaleType.FIT_CENTER) {
                holder.binding.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                holder.binding.imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                holder.binding.imageView.setScale(1.0f, true) // Reset zoom when going back to full view
            }
        }

        // PHASE 1: BLUR-UP PREVIEW (Tiny thumb variants load in milliseconds)
        holder.binding.imageViewBlur.load(thumbUrl) {
            precision(Precision.EXACT)
            size(40, 40) // Create organic pixel blending
        }

        // PHASE 2: HIGH-RES FOREGROUND (4K Photography)
        holder.binding.imageView.load(originalUrl) {
            crossfade(600)
            placeholder(holder.binding.imageView.drawable)
            error(R.drawable.loading_placeholder)
            listener(
                onSuccess = { _, _ ->
                    onImageRendered()
                }
            )
        }
    }

    override fun getItemCount(): Int = images.size
}
