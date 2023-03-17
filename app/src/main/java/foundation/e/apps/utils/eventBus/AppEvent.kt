/*
 *
 *  * Copyright ECORP SAS 2022
 *  * Apps  Quickly and easily install Android apps onto your device!
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.utils.eventBus

import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.utils.enums.ResultStatus

sealed class AppEvent(val data: Any) {
    class SignatureMissMatchError(packageName: String) : AppEvent(packageName)
    class UpdateEvent(result: ResultSupreme.WorkError<ResultStatus>) : AppEvent(result)

    class InvalidAuthEvent(authName: String) : AppEvent(authName)
}
