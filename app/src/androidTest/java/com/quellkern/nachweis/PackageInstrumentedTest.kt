package com.quellkern.nachweis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal instrumented smoke test. The application id is flavor-dependent
 * (com.quellkern.nachweis for production, .demo for demo), so this asserts the
 * shared namespace prefix rather than an exact id.
 */
@RunWith(AndroidJUnit4::class)
class PackageInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(appContext.packageName.startsWith("com.quellkern.nachweis"))
    }
}
