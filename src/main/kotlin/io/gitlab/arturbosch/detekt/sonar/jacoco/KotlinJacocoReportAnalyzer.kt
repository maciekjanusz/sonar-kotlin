/*
 * SonarQube Java
 * Copyright (C) 2010-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.gitlab.arturbosch.detekt.sonar.jacoco

import com.google.common.collect.Maps
import io.gitlab.arturbosch.detekt.sonar.foundation.KotlinClasspath
import org.apache.commons.lang.StringUtils
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.analysis.ISourceFileCoverage
import org.jacoco.core.data.ExecutionDataStore
import org.sonar.api.batch.fs.InputFile
import org.sonar.api.batch.sensor.SensorContext
import org.sonar.api.batch.sensor.coverage.CoverageType
import org.sonar.api.batch.sensor.coverage.NewCoverage
import org.sonar.plugins.jacoco.ExecutionDataVisitor
import org.sonar.plugins.jacoco.JaCoCoExtensions
import org.sonar.plugins.jacoco.JacocoReportReader
import org.sonar.plugins.java.api.JavaResourceLocator
import java.io.File
import java.util.ArrayList


private const val NO_JACOCO_EXECUTION_DATA_MESSAGE =
    "Project coverage is set to 0% as no JaCoCo execution data has been dumped: {}"
private const val NO_CLASS_FILES_MESSAGE =
    "No JaCoCo analysis of project coverage can be done since there is no class files."

private const val NO_INFORMATION_ABOUT_COVERAGE_DATA_MESSAGE = "No information about coverage per test."
private const val COVERAGE_PER_TEST_MESSAGE = "Information about coverage per test has been collected."

private const val NO_DATA_COLLECTED_MESSAGE =
    "Coverage information was not collected. Perhaps you forget to include debug information into compiled classes?"

@Suppress("ALL")
class KotlinJacocoReportAnalyzer @JvmOverloads constructor(
    private val javaResourceLocator: JavaResourceLocator,
    private val kotlinClasspath: KotlinClasspath,
    private val readCoveragePerTests: Boolean = true,
    private val report: File) {

    private var classFilesCache: MutableMap<String, File> = Maps.newHashMap()

    private fun fullyQualifiedClassName(packageName: String, simpleClassName: String): String =
        if ("" == packageName) ""
        else packageName + "/" + StringUtils.substringBeforeLast(simpleClassName, ".")

    private fun getResource(coverage: ISourceFileCoverage): InputFile? {
        val className = fullyQualifiedClassName(coverage.packageName, coverage.name)

        // Do not save measures on resource which doesn't exist in the context
        val inputFile = javaResourceLocator.findResourceByClassName(className) ?: return null
        return if (inputFile.type() == InputFile.Type.TEST) {
            null
        } else inputFile

    }

    fun analyse(context: SensorContext) {
        for (classesDir in kotlinClasspath.binaryDirs) {
            populateClassFilesCache(classesDir, "")
        }

        if (classFilesCache.isEmpty()) {
            JaCoCoExtensions.LOG.info(NO_CLASS_FILES_MESSAGE)
            return
        }
        val jacocoExecutionData = report
        readExecutionData(jacocoExecutionData, context)

        classFilesCache = Maps.newHashMap()
    }

    private fun populateClassFilesCache(dir: File, path: String) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                populateClassFilesCache(file, path + file.name + "/")
            } else if (file.name.endsWith(".class")) {
                val className = path + StringUtils.removeEnd(file.name, ".class")
                classFilesCache.put(className, file)
            }
        }
    }

    private fun readExecutionData(jacocoExecutionData: File?, context: SensorContext) {
        var newJacocoExecutionData = jacocoExecutionData
        if (newJacocoExecutionData == null || !newJacocoExecutionData.isFile) {
            JaCoCoExtensions.LOG.info(NO_JACOCO_EXECUTION_DATA_MESSAGE, newJacocoExecutionData)
            newJacocoExecutionData = null
        }

        val executionDataVisitor = ExecutionDataVisitor()
        val jacocoReportReader = JacocoReportReader(newJacocoExecutionData)
            .readJacocoReport(executionDataVisitor, executionDataVisitor)

        val collectedCoveragePerTest = readCoveragePerTests(executionDataVisitor, jacocoReportReader)

        val coverageBuilder = jacocoReportReader!!.analyzeFiles(
            executionDataVisitor.merged, classFilesCache.values)

        var analyzedResources = 0
        for (coverage in coverageBuilder.sourceFiles) {
            val inputFile = getResource(coverage)
            if (inputFile != null) {
                val newCoverage = context.newCoverage().onFile(inputFile).ofType(CoverageType.UNIT)
                analyzeFile(newCoverage, inputFile, coverage)
                newCoverage.save()
                analyzedResources++
            }
        }

        when {
            analyzedResources == 0 -> JaCoCoExtensions.LOG.warn(NO_DATA_COLLECTED_MESSAGE)
            collectedCoveragePerTest -> JaCoCoExtensions.LOG.info(COVERAGE_PER_TEST_MESSAGE)
            newJacocoExecutionData != null -> JaCoCoExtensions.LOG.info(NO_INFORMATION_ABOUT_COVERAGE_DATA_MESSAGE)
        }
    }

    private fun readCoveragePerTests(executionDataVisitor: ExecutionDataVisitor,
                                     jacocoReportReader: JacocoReportReader): Boolean {
        var collectedCoveragePerTest = false
        if (readCoveragePerTests) {
            for ((key, value) in executionDataVisitor.sessions) {
                if (analyzeLinesCoveredByTests(key, value, jacocoReportReader)) {
                    collectedCoveragePerTest = true
                }
            }
        }
        return collectedCoveragePerTest
    }

    private fun analyzeLinesCoveredByTests(sessionId: String,
                                           executionDataStore: ExecutionDataStore,
                                           jacocoReportReader: JacocoReportReader): Boolean {
        val i = sessionId.indexOf(' ')
        if (i < 0) {
            return false
        }

        var result = false
        val coverageBuilder = jacocoReportReader.analyzeFiles(
            executionDataStore, classFilesOfStore(executionDataStore))
        for (coverage in coverageBuilder.sourceFiles) {
            val resource = getResource(coverage)
            if (resource != null) {
                val coveredLines = coveredLines(coverage)
                if (!coveredLines.isEmpty()) {
                    result = true
                }
            }
        }
        return result
    }

    private fun classFilesOfStore(executionDataStore: ExecutionDataStore): Collection<File> {
        return executionDataStore.contents
            .map { it.name }
            .mapNotNullTo(ArrayList()) { classFilesCache[it] }
    }

    private fun coveredLines(coverage: ISourceFileCoverage): List<Int> {
        val coveredLines = ArrayList<Int>()
        lines@ for (lineId in coverage.firstLine..coverage.lastLine) {
            val line = coverage.getLine(lineId)
            when (line.instructionCounter.status) {
                ICounter.FULLY_COVERED, ICounter.PARTLY_COVERED -> coveredLines.add(lineId)
                ICounter.NOT_COVERED -> {
                }
                else -> continue@lines
            }
        }
        return coveredLines
    }

    private fun analyzeFile(newCoverage: NewCoverage, resource: InputFile, coverage: ISourceFileCoverage) {
        JaCoCoExtensions.LOG.info("Analyzing file: {}//{}", resource.relativePath(), coverage.name)
        var lineId = coverage.firstLine
        statuses@ while (lineId <= coverage.lastLine && resource.lines() >= lineId) {
            val hits: Int
            val line = coverage.getLine(lineId)
            hits = when (line.instructionCounter.status) {
                ICounter.FULLY_COVERED, ICounter.PARTLY_COVERED -> 1
                ICounter.NOT_COVERED -> 0
                ICounter.EMPTY -> {
                    lineId++
                    continue@statuses
                }
                else -> {
                    JaCoCoExtensions.LOG.warn("Unknown status for line {} in {}", lineId, resource)
                    lineId++
                    continue@statuses
                }
            }
            newCoverage.lineHits(lineId, hits)
            val branchCounter = line.branchCounter
            val conditions = branchCounter.totalCount
            if (conditions > 0) {
                val coveredConditions = branchCounter.coveredCount
                newCoverage.conditions(lineId, conditions, coveredConditions)
            }
            lineId++
        }
    }

}
