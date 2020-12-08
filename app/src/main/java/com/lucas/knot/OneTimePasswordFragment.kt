package com.lucas.knot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.lucas.knot.databinding.OneTimePasswordFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OneTimePasswordFragment : Fragment() {
    private val viewModel: OneTimePasswordViewModel by viewModels()
    private lateinit var binding: OneTimePasswordFragmentBinding
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        binding = OneTimePasswordFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fabNext.setOnClickListener {
            findNavController().navigate(OneTimePasswordFragmentDirections.actionOneTimePasswordFragmentToUpdateUserProfileFragment())
        }
    }

}