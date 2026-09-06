package com.phonemood

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.phonemood.analysis.*
import com.phonemood.ui.AnalysisContent
import com.phonemood.ui.AnalysisViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AnalysisFeatureTest {
    @Test fun frozenJsonCanBeSharedAndSavedThroughContentResolver() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val facts=Facts(System.currentTimeMillis(),"UTC",1,null,emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        val data=PeriodDatasetBuilder.build(facts,7)
        val content=AnalysisContent(data,StatisticalEngine.analyze(data))
        val vm=AnalysisViewModel(app)
        val uri=vm.share(content)
        val shared=app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val doc=Json.parseToJsonElement(shared).jsonObject
        assertEquals("2.0",doc.getValue("schema_version").jsonPrimitive.content)
        assertEquals(7,doc.getValue("days").jsonArray.size)
        assertTrue(doc.getValue("analysis_matrix").jsonObject.getValue("rows").jsonArray.isEmpty())
        val target=File(app.cacheDir,"analysis-exports/save-test.json")
        try {
            vm.prepareExport(content)
            vm.save(FileProvider.getUriForFile(app,"${app.packageName}.files",target))
            assertEquals(shared,target.readText())
        } finally { target.delete();app.contentResolver.delete(uri,null,null) }
    }

}
