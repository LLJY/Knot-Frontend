package com.lucas.knot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lucas.knot.databinding.LoginPhoneNumberFragmentBinding

class LoginPhoneNumber : Fragment() {
    private val viewModel: LoginPhoneNumberViewModel by activityViewModels()
    private lateinit var binding: LoginPhoneNumberFragmentBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        binding = LoginPhoneNumberFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fabNext.setOnClickListener {
            findNavController().navigate(LoginPhoneNumberDirections.actionLoginPhoneNumberToOneTimePasswordFragment())
        }
    }

}