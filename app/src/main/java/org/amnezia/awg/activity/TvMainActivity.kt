/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.amnezia.awg.databinding.Keyed
import java.io.File

/**
 * TV launcher entry point that directs TV users directly to Zaza MainActivity,
 * which is optimized for TV, tablets, D-pad navigation, and landscape displays.
 */
class TvMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    class KeyedFile(val file: File, private val forcedKey: String? = null) : Keyed<String> {
        override val key: String
            get() = forcedKey ?: if (file.isDirectory) "${file.name}/" else file.name
    }
}
