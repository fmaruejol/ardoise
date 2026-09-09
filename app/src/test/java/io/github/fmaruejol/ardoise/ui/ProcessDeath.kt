package io.github.fmaruejol.ardoise.ui

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle

fun SavedStateHandle.throughProcessDeath(): SavedStateHandle {
    val saved = savedStateProvider().saveState()
    val parcel = Parcel.obtain()
    return try {
        parcel.writeBundle(saved)
        parcel.setDataPosition(0)
        val restored = parcel.readBundle(SavedStateHandle::class.java.classLoader)
        SavedStateHandle.createHandle(restored, null)
    } finally {
        parcel.recycle()
    }
}
