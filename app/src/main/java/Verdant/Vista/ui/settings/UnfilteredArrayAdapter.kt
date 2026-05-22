package Verdant.Vista.ui.settings

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * A specialized ArrayAdapter that disables the filtering logic of AutoCompleteTextView.
 * This ensures that when the user taps a dropdown, they ALWAYS see the full list of options,
 * even if a selection was already made.
 */
class UnfilteredArrayAdapter<T>(context: Context, resource: Int, objects: Array<T>) 
    : ArrayAdapter<T>(context, resource, objects) {

    private val noFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            results.values = objects
            results.count = objects.size
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter {
        return noFilter
    }
}
