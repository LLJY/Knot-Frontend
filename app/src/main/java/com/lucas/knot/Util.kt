package com.lucas.knot

import android.util.Patterns

fun CharSequence.isValidEmail() = !isNullOrEmpty() && Patterns.EMAIL_ADDRESS.matcher(this).matches()
fun CharSequence.isValidPhoneNumber() = !isNullOrEmpty() && Patterns.PHONE.matcher(this).matches()