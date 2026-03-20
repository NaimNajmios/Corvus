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
import com.najmi.corvus.domain.model.PipelineStep
import com.najmi.corvus.domain.usecase.CompositeFactCheckPipeline
import com.najmi.corvus.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking

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
        
        // Initial foreground info
        setForeground(createForegroundInfo(PipelineStep.IDLE))

        return try {
            val checkResult = compositePipeline.check(inputText) { step ->
                // Update progress for live notification
                val progress = workDataOf("step" to step.name)
                setProgressAsync(progress)
                
                // Update foreground notification
                runBlocking {
                    setForeground(createForegroundInfo(step))
                }
            }

            historyRepository.saveResult(checkResult)

            // Show final result notification
            val title = "Fact Check Result: ${checkResult.verdictName}"
            val summary = checkResult.summary
            notificationHelper.showResultNotification(title, summary, checkResult.id)

            Result.success(workDataOf("resultId" to checkResult.id))
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to e.message))
        }
    }

    private fun createForegroundInfo(step: PipelineStep): ForegroundInfo {
        val builder = notificationHelper.getProgressNotificationBuilder(step)
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

// Extension to get verdict name easily
private val CorvusCheckResult.verdictName: String
    get() = when (this) {
        is CorvusCheckResult.GeneralResult -> verdict.name
        is CorvusCheckResult.QuoteResult -> quoteVerdict.name
        is CorvusCheckResult.CompositeResult -> compositeVerdict.name
        is CorvusCheckResult.ViralHoaxResult -> "FALSE"
    }

private val CorvusCheckResult.summary: String
    get() = when (this) {
        is CorvusCheckResult.GeneralResult -> explanation
        is CorvusCheckResult.QuoteResult -> contextExplanation
        is CorvusCheckResult.CompositeResult -> compositeSummary
        is CorvusCheckResult.ViralHoaxResult -> summary
    }
