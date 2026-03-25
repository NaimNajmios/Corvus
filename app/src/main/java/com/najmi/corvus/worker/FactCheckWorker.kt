package com.najmi.corvus.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.najmi.corvus.data.repository.HistoryRepository
import com.najmi.corvus.domain.model.CorvusCheckResult
import com.najmi.corvus.domain.model.CheckingStatus
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.usecase.CompositeFactCheckPipeline
import com.najmi.corvus.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Locale

@HiltWorker
class FactCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val compositePipeline: CompositeFactCheckPipeline,
    private val historyRepository: HistoryRepository
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result {
        val inputText = inputData.getString("inputText") ?: return Result.failure()
        
        setForeground(createForegroundInfo(CheckingStatus.IDLE))

        return try {
            val checkResult = compositePipeline.check(inputText) { step ->
                val status = convertToCheckingStatus(step)
                val progress = workDataOf("step" to step.name)
                setProgress(progress)
                setForeground(createForegroundInfo(status))
            }

            historyRepository.saveResult(checkResult)

            notificationHelper.cancelProgressNotification()

            val title = "Fact Check Result: ${checkResult.verdictName}"
            val summary = checkResult.summary
            notificationHelper.showResultNotification(title, summary, checkResult.id)

            Result.success(workDataOf("resultId" to checkResult.id))
        } catch (e: Exception) {
            notificationHelper.cancelProgressNotification()
            Result.failure(workDataOf("error" to e.message))
        }
    }

    private fun convertToCheckingStatus(step: PipelineStep): CheckingStatus {
        return when (step) {
            PipelineStep.IDLE -> CheckingStatus.IDLE
            PipelineStep.CHECKING_VIRAL_DATABASE -> CheckingStatus.CHECKING_VIRAL_DATABASE
            PipelineStep.CHECKING_KNOWN_FACTS -> CheckingStatus.CHECKING_KNOWN_FACTS
            PipelineStep.DISSECTING -> CheckingStatus.DISSECTING
            PipelineStep.CHECKING_SUB_CLAIMS -> CheckingStatus.CHECKING_SUB_CLAIMS
            PipelineStep.RETRIEVING_SOURCES -> CheckingStatus.RETRIEVING_SOURCES
            PipelineStep.ANALYZING -> CheckingStatus.ANALYZING
            PipelineStep.DONE -> CheckingStatus.DONE
        }
    }

    private fun createForegroundInfo(status: CheckingStatus): ForegroundInfo {
        val builder = notificationHelper.getProgressNotificationBuilder(status)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.PROGRESS_NOTIFICATION_ID,
                builder.build(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            ForegroundInfo(NotificationHelper.PROGRESS_NOTIFICATION_ID, builder.build())
        }
    }
}

private val CorvusCheckResult.verdictName: String
    get() = when (this) {
        is CorvusCheckResult.GeneralResult -> verdict.name
        is CorvusCheckResult.QuoteResult -> quoteVerdict.name
        is CorvusCheckResult.CompositeResult -> compositeVerdict.name
        is CorvusCheckResult.ViralHoaxResult -> "FALSE"
    }.split("_").joinToString(" ") { word ->
        word.lowercase(Locale.getDefault()).replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    }

private val CorvusCheckResult.summary: String
    get() = when (this) {
        is CorvusCheckResult.GeneralResult -> explanation
        is CorvusCheckResult.QuoteResult -> contextExplanation
        is CorvusCheckResult.CompositeResult -> compositeSummary
        is CorvusCheckResult.ViralHoaxResult -> summary
    }
