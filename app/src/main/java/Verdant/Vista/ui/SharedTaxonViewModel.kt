package Verdant.Vista.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import Verdant.Vista.data.db.FavoriteEntity
import Verdant.Vista.data.model.Taxon

class SharedTaxonViewModel : ViewModel() {
    private val _selectedTaxon = MutableLiveData<Taxon?>()
    val selectedTaxon: LiveData<Taxon?> = _selectedTaxon

    private val _selectedFavorite = MutableLiveData<FavoriteEntity?>()
    val selectedFavorite: LiveData<FavoriteEntity?> = _selectedFavorite

    private val _requestedTaxonId = MutableLiveData<Int?>()
    val requestedTaxonId: LiveData<Int?> = _requestedTaxonId

    private val _refreshRequested = MutableLiveData<Boolean>()
    val refreshRequested: LiveData<Boolean> = _refreshRequested

    fun selectTaxon(taxon: Taxon?) {
        _selectedFavorite.value = null
        _selectedTaxon.value = taxon
    }

    fun selectFavorite(favorite: FavoriteEntity?) {
        _selectedTaxon.value = null
        _selectedFavorite.value = favorite
    }

    fun requestTaxonId(id: Int) {
        _requestedTaxonId.value = id
    }

    fun clearRequestedTaxonId() {
        _requestedTaxonId.value = null
    }

    fun requestRefresh() {
        _refreshRequested.value = true
    }

    fun clearRefreshRequest() {
        _refreshRequested.value = false
    }
}
