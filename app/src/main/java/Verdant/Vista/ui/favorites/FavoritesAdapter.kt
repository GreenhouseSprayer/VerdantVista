package Verdant.Vista.ui.favorites

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import Verdant.Vista.R
import Verdant.Vista.data.db.FavoriteEntity
import Verdant.Vista.databinding.ItemSpeciesBinding
import Verdant.Vista.util.PhotoUtils

class FavoritesAdapter(
    private val onItemClick: (FavoriteEntity) -> Unit
) : ListAdapter<FavoriteEntity, FavoritesAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemSpeciesBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSpeciesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val favorite = getItem(position)
        val commonName = (favorite.commonName ?: favorite.scientificName).split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            
        holder.binding.favCommonName.text = commonName
        holder.binding.favScientificName.text = favorite.scientificName
        
        holder.binding.favImage.load(PhotoUtils.getWidgetResUrl(favorite.imageUrl)) {
            crossfade(true)
            placeholder(R.drawable.loading_placeholder)
            error(R.drawable.loading_placeholder)
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(favorite)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FavoriteEntity>() {
        override fun areItemsTheSame(oldItem: FavoriteEntity, newItem: FavoriteEntity): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: FavoriteEntity, newItem: FavoriteEntity): Boolean {
            return oldItem == newItem
        }
    }
}
