package com.leondeklerk.starling.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.leondeklerk.starling.databinding.FragmentLibraryBinding

/**
 * A simple [Fragment] responsible for showing all media on the device (device only) in a folder structure.
 * This is also responsible for setting sync settings on all folders.
 */
class LibraryFragment : Fragment() {

    private lateinit var libraryViewModel: LibraryViewModel
    private var _binding: FragmentLibraryBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Instantiate the viewModel
        libraryViewModel =
            ViewModelProvider(this).get(LibraryViewModel::class.java)

        // Inflate the bindings
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Update the sample text.
        val textView: TextView = binding.textLibrary
        libraryViewModel.text.observe(
            viewLifecycleOwner,
            {
                textView.text = it
            }
        )
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
