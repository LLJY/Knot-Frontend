package com.lucas.knot

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.isDigitsOnly
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.lucas.knot.databinding.OneTimePasswordFragmentBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OneTimePasswordFragment : Fragment() {
    private val viewModel: OneTimePasswordViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var pd: ProgressDialog
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
        // viewModel will countdown and update the tv
        viewModel.calculateSecondsRemaining().observe(viewLifecycleOwner) {
            if (it != -1L) {
                binding.timeRemainingTv.text = getString(R.string.otp_time_remaining, "${it}s")
            } else {
                binding.timeRemainingTv.text = getString(R.string.otp_time_remaining, "${it}s")
            }
        }
        // hide the FAB by default
        binding.fabNext.hide()
        binding.otpField.editText!!.addTextChangedListener {
            // verify the field and show the FAB if it is correct
            if (it?.isDigitsOnly() == true) {
                viewModel.OTP = it.toString().toInt()
                binding.fabNext.show()
                binding.otpField.error = null
            } else {
                binding.otpField.error = "Invalid OTP"
                binding.fabNext.hide()
            }
        }
        binding.fabNext.setOnClickListener {
            pd.show()
            viewModel.verifyOTP(mainViewModel.phoneNumber, viewModel.OTP).observe(viewLifecycleOwner) {
                pd.dismiss()
                if (it.isSuccessful) {
                    // if successfully verified and the user is a first time user, direct them to update profile
                    if (it.isSignUp) {
                        findNavController().navigate(OneTimePasswordFragmentDirections.actionOneTimePasswordFragmentToUpdateUserProfileFragment())
                    } else {
                        // if not first time user open the home activity
                        val intent = Intent(this.requireActivity(), ChatListActivity::class.java)
                        startActivity(intent)
                    }
                } else {
                    Snackbar.make(binding.root, getString(R.string.snackbar_otp_error), Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

}