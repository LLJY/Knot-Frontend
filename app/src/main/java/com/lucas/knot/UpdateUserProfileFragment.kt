package com.lucas.knot

import android.app.Activity.RESULT_OK
import android.content.ContentResolver
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.lucas.knot.databinding.UpdateUserProfileFragmentBinding
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class UpdateUserProfileFragment : Fragment() {
    private val PICK_IMAGE_REQUEST = 71
    private var contentResolver: ContentResolver? = null
    private val viewModel: UpdateUserProfileViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: UpdateUserProfileFragmentBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = UpdateUserProfileFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentResolver = activity?.contentResolver
        binding.fabNext.setOnClickListener {
            if (binding.usernameText.editText!!.text.isNotBlank()) {
                // if username field is valid, proceed with updating
                viewModel.updateProfile(mainViewModel.phoneNumber).observe(viewLifecycleOwner) {
                    if (it) {
                        // launch the other activity if successful
                        val intent = Intent(activity, ChatListActivity::class.java).apply {
                            putExtra("IS_NEW_USER", false)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity?.finish()
                        startActivity(intent)
                    } else {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.snackbar_generic_error),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                binding.usernameText.error = "Must not be empty!"
            }
        }
        binding.usernameText.editText!!.addTextChangedListener {
            // if the username text is not blank, remove the indicated errors and assign value to viewmodel
            if (!it.isNullOrBlank()) {
                binding.usernameText.error = null
                viewModel.username = it.toString()
            }
        }
        binding.bioText.editText!!.addTextChangedListener {
            // ensure we do not have empty strings as bios
            if (!it.isNullOrEmpty()) {
                viewModel.bio = it.toString()
            }
        }
        binding.profileImage.setOnClickListener {
            chooseImage()
        }
    }

    private fun chooseImage() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
            && data != null && data.data != null
        ) {
            val filePath: Uri? = data.data
            if (filePath != null) {
                // upload the file, and if it is successful, set the image on the image button
                viewModel.uploadFile(filePath).observe(viewLifecycleOwner) {
                    // ensure that upload was successful
                    if (it != "") {
                        val source =
                            contentResolver?.let { it1 -> ImageDecoder.createSource(it1, filePath) }
                        if (source != null) {
                            val bitmap = ImageDecoder.decodeBitmap(source)
                            //val bitmap = MediaStore.Images.Media.getBitmap(activity?.contentResolver, filePath)
                            binding.profileImage.setImageBitmap(bitmap)
                        }

                    } else {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.snackbar_generic_error),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }

                }
            } else {
                Snackbar.make(
                    binding.root,
                    getString(R.string.snackbar_generic_error),
                    Snackbar.LENGTH_SHORT
                ).show()
            }

        }

    }


}