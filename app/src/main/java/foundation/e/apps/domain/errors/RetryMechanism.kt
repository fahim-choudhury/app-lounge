/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.domain.errors

class RetryMechanism {

    private var autoRetryCount = 0

    /**
     * Wrap the code to code to retry execution.
     */
    fun wrapWithRetry(
        retryBlock: () -> Unit,
        retryFailureBlock: () -> Unit
    ) {
        if (!retryEvaluator(retryBlock)) {
            retryFailureBlock()
        }
    }

    /**
     * Example of where this function can be called:
     * - If user presses "Log out" or "Retry" from an error dialog.
     */
    fun resetRetryCondition() {
        autoRetryCount = 0
    }

    /**
     * The actual block to do multiple retries.
     * We can do some fancy stuff like exponential back-off using recursions.
     *
     * @return true if retry conditions have not expired, false otherwise.
     */
    private fun retryEvaluator(
        retryBlock: () -> Unit
    ): Boolean {
        if (shouldFailRetry()) return false
        retryBlock()
        updateAutoRetryCondition()
        return true
    }

    private fun updateAutoRetryCondition() {
        autoRetryCount++
    }

    private fun shouldFailRetry(): Boolean {
        return autoRetryCount > 0
    }
}
