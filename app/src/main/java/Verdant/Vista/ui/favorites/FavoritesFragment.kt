package Verdant.Vista.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import Verdant.Vista.R
import Verdant.Vista.VerdantVistaApplication
import Verdant.Vista.databinding.FragmentFavoritesBinding
import Verdant.Vista.ui.SharedTaxonViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: FavoritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFavorites()
        setupNavigationObserver()
    }

    private fun setupRecyclerView() {
        val sharedViewModel = ViewModelProvider(requireActivity()).get(SharedTaxonViewModel::class.java)
        adapter = FavoritesAdapter { favorite ->
            sharedViewModel.requestTaxonId(favorite.id.toInt())
            findNavController().navigate(R.id.action_nav_favorites_to_nav_discover)
        }
        binding.recyclerFavorites.adapter = adapter
    }

    private fun setupFavorites() {
        val app = requireActivity().application as VerdantVistaApplication
        val repository = app.repository

        viewLifecycleOwner.lifecycleScope.launch {
            repository.getFavorites().collectLatest { favorites ->
                adapter.submitList(favorites)
                
                // Toggle empty state visibility safely to avoid NPE during rapid navigation
                _binding?.let { b ->
                    if (favorites.isEmpty()) {
                        b.emptyState.visibility = View.VISIBLE
                        b.recyclerFavorites.visibility = View.GONE
                    } else {
                        b.emptyState.visibility = View.GONE
                        b.recyclerFavorites.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupNavigationObserver() {
        val sharedViewModel = ViewModelProvider(requireActivity()).get(SharedTaxonViewModel::class.java)
        sharedViewModel.requestedTaxonId.observe(viewLifecycleOwner) { taxonId ->
            if (taxonId != null) {
                // If we have a pending request from the widget, navigate to Discover
                findNavController().navigate(R.id.action_nav_favorites_to_nav_discover)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
