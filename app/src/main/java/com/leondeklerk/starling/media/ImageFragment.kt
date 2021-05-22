package com.leondeklerk.starling.media

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.databinding.FragmentImageBinding

class ImageFragment : Fragment() {

    private lateinit var viewModel: ImageViewModel
    private var _binding: FragmentImageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create the viewModel
        viewModel = ViewModelProvider(this).get(ImageViewModel::class.java)

        val imageItem = ImageFragmentArgs.fromBundle(requireArguments()).imageItem as ImageItem

        // Inflate the binding
        _binding = FragmentImageBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this

        val imageView = binding.imageView

        // Load image with Glide into the imageView
        Glide.with(imageView)
            .load(imageItem.contentUri)
            .placeholder(ColorDrawable(Color.GRAY))
            .thumbnail(0.5f)
            .centerCrop()
            .into(imageView)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
