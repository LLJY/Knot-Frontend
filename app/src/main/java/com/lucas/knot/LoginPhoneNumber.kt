package com.lucas.knot

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lucas.knot.databinding.LoginPhoneNumberFragmentBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LoginPhoneNumber : Fragment() {
    private val viewModel: LoginPhoneNumberViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val oneTimePasswordViewModel: OneTimePasswordViewModel by activityViewModels()
    private lateinit var binding: LoginPhoneNumberFragmentBinding

    @Inject
    lateinit var pd: ProgressDialog
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        binding = LoginPhoneNumberFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.fabNext.hide()
        binding.otpField.editText!!.setText(mainViewModel.phoneNumber)
        binding.otpField.editText!!.addTextChangedListener {
            if (it?.isValidPhoneNumber() == true) {
                mainViewModel.phoneNumber = it.toString().trim()
                binding.fabNext.show()
                binding.otpField.error = null
            } else {
                binding.otpField.error = "Enter a valid phone number!"
                binding.fabNext.hide()
            }
        }
        binding.fabNext.setOnClickListener {
            pd.show()
            viewModel.requestOTP(mainViewModel.phoneNumber).observe(viewLifecycleOwner) {
                pd.dismiss()
                // set the otp data for the next viewModel
                oneTimePasswordViewModel.setOTPTime(it.toLong())
                findNavController().navigate(LoginPhoneNumberDirections.actionLoginPhoneNumberToOneTimePasswordFragment())
            }
        }
    }

}