/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.net.benchmarktests

import android.net.NetworkStats.NonMonotonicObserver
import android.net.NetworkStatsCollection
import android.net.netstats.NetworkStatsDataMigrationUtils.PREFIX_UID
import android.os.DropBoxManager
import androidx.test.InstrumentationRegistry
import com.android.internal.util.FileRotator
import com.android.internal.util.FileRotator.Reader
import com.android.server.connectivity.benchmarktests.R
import com.android.server.net.NetworkStatsRecorder
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.test.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock

@RunWith(JUnit4::class)
class NetworkStatsTest {
    companion object {
        private val DEFAULT_BUFFER_SIZE = 8192
        private val FILE_CACHE_WARM_UP_REPEAT_COUNT = 10
        private val TEST_REPEAT_COUNT = 10
        private val UID_COLLECTION_BUCKET_DURATION_MS = TimeUnit.HOURS.toMillis(2)
        private val UID_RECORDER_ROTATE_AGE_MS = TimeUnit.DAYS.toMillis(15)
        private val UID_RECORDER_DELETE_AGE_MS = TimeUnit.DAYS.toMillis(90)

        private val testFilesDir by lazy {
            // These file generated by using real user dataset which has many uid records
            // and agreed to share the dataset for testing purpose. These dataset can be
            // extracted from rooted devices by using
            // "adb pull /data/misc/apexdata/com.android.tethering/netstats" command.
            val zipInputStream =
                ZipInputStream(getInputStreamForResource(R.raw.netstats_many_uids_zip))
            unzipToTempDir(zipInputStream)
        }

        private val uidTestFiles: List<File> by lazy {
            getSortedListForPrefix(testFilesDir, "uid")
        }

        // Test results shows the test cases who read the file first will take longer time to
        // execute, and reading time getting shorter each time due to file caching mechanism.
        // Read files several times prior to tests to minimize the impact.
        // This cannot live in setUp() since the time spent on the file reading will be
        // attributed to the time spent on the individual test case.
        @JvmStatic
        @BeforeClass
        fun setUpOnce() {
            repeat(FILE_CACHE_WARM_UP_REPEAT_COUNT) {
                val collection = NetworkStatsCollection(UID_COLLECTION_BUCKET_DURATION_MS)
                for (file in uidTestFiles) {
                    readFile(file, collection)
                }
            }
        }

        private fun getInputStreamForResource(resourceId: Int): DataInputStream =
            DataInputStream(
                InstrumentationRegistry.getContext()
                    .getResources().openRawResource(resourceId)
            )

        private fun unzipToTempDir(zis: ZipInputStream): File {
            val statsDir =
                Files.createTempDirectory(NetworkStatsTest::class.simpleName).toFile()
            generateSequence { zis.nextEntry }.forEach { entry ->
                FileOutputStream(File(statsDir, entry.name)).use {
                    zis.copyTo(it, DEFAULT_BUFFER_SIZE)
                }
            }
            return statsDir
        }

        // List [xt|uid|uid_tag].<start>-<end> files under the given directory.
        private fun getSortedListForPrefix(statsDir: File, prefix: String): List<File> {
            assertTrue(statsDir.exists())
            return statsDir.list() { dir, name -> name.startsWith("$prefix.") }
                .orEmpty()
                .map { it -> File(statsDir, it) }
                .sorted()
        }

        private fun readFile(file: File, reader: Reader) =
            BufferedInputStream(file.inputStream()).use {
                reader.read(it)
            }
    }

    @Test
    fun testReadCollection_manyUids() {
        // The file cache is warmed up by the @BeforeClass method, so now the test can repeat
        // this a number of time to have a stable number.
        repeat(TEST_REPEAT_COUNT) {
            val collection = NetworkStatsCollection(UID_COLLECTION_BUCKET_DURATION_MS)
            for (file in uidTestFiles) {
                readFile(file, collection)
            }
        }
    }

    @Test
    fun testReadFromRecorder_manyUids() {
        val mockObserver = mock<NonMonotonicObserver<String>>()
        val mockDropBox = mock<DropBoxManager>()
        repeat(TEST_REPEAT_COUNT) {
            val recorder = NetworkStatsRecorder(
                FileRotator(
                    testFilesDir, PREFIX_UID, UID_RECORDER_ROTATE_AGE_MS, UID_RECORDER_DELETE_AGE_MS
                ),
                mockObserver,
                mockDropBox,
                PREFIX_UID,
                UID_COLLECTION_BUCKET_DURATION_MS,
                false /* includeTags */,
                false /* wipeOnError */
            )
            recorder.orLoadCompleteLocked
        }
    }

    inline fun <reified T> mock(): T = mock(T::class.java)
}
